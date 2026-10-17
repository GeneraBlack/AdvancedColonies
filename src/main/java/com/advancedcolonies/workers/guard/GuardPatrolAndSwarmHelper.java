package com.advancedcolonies.workers.guard;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.CitizenCommonConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.ai.statemachine.AIOneTimeEventTarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Domain helper for smart Guard patrol dispersion, colony security coverage, and tactical
 * squad convergence during raids or large mob swarms:
 * <ul>
 *   <li><b>Peaceful Dispersion</b>: Prevents Guards from marching as a single clumped blob by
 *       assigning each Guard an individual patrol target that maximizes separation from other Guards
 *       and prioritizes buildings that have gone the longest without a security visit.</li>
 *   <li><b>Tactical Group Response</b>: Detects active colony raids and large mob swarms
 *       (&ge; {@code guardSwarmMobThreshold} hostiles) and rallies nearby Guards to converge
 *       as a coordinated squad at the hotspot before dispersing again once clear.</li>
 *   <li><b>Security Happiness Synergy</b>: Evaluates real spatial guard coverage across colony
 *       buildings to grant a coverage bonus to the colony's Security happiness factor.</li>
 * </ul>
 */
public final class GuardPatrolAndSwarmHelper {

    /** Radius (in blocks) around an engaged Guard scanned for hostile mob swarms. */
    private static final double SWARM_SCAN_RADIUS = 20.0D;

    /** Duration (in ticks) that a detected mob swarm hotspot remains active after its last update. */
    private static final long HOTSPOT_EXPIRY_TICKS = 200L;

    /** Window (in ticks, 10 minutes = 12,000 ticks) used to evaluate recent building patrol coverage. */
    private static final long COVERAGE_WINDOW_TICKS = 12000L;

    /** Maximum Security happiness multiplier bonus (+25%) granted for full spatial guard coverage. */
    private static final double MAX_COVERAGE_HAPPINESS_BONUS = 0.25D;

    /** Active patrol destination per Guard (keyed by {@code colonyId << 32 | citizenId}). */
    private static final Map<Long, BlockPos> ACTIVE_GUARD_PATROL_TARGETS = new ConcurrentHashMap<>();

    /** Last server game-tick timestamp when a colony building was visited by a patrolling Guard. */
    private static final Map<BlockPos, Long> BUILDING_LAST_PATROLLED_TICK = new ConcurrentHashMap<>();

    /** Active mob-swarm / raid combat hotspots per colony ID. */
    private static final Map<Integer, ThreatHotspot> ACTIVE_HOTSPOTS_BY_COLONY = new ConcurrentHashMap<>();

    private record ThreatHotspot(
            @NotNull BlockPos position,
            @NotNull WeakReference<LivingEntity> primaryThreat,
            int hostileCount,
            long timestampTick) {
    }

    private GuardPatrolAndSwarmHelper() {
    }

    /**
     * Scans the area around an engaged Guard for a mob swarm or active raid and calls nearby
     * fellow Guards to converge as a coordinated group when a threshold threat is present.
     *
     * @param worker         the Guard currently in combat
     * @param buildingGuards the Guard's assigned building
     */
    public static void tickCombatSwarmDetection(
            @Nullable final AbstractEntityCitizen worker,
            @Nullable final IGuardBuilding buildingGuards) {

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableTacticalGroupResponseOnSwarmsAndRaids.get()
                || worker == null
                || buildingGuards == null
                || worker.level().isClientSide()) {
            return;
        }

        final IColony colony = buildingGuards.getColony();
        if (colony == null) {
            return;
        }

        final Level level = worker.level();
        final long now = level.getGameTime();
        // Throttle entity scan to once every ~1.5 seconds per fighting guard
        if ((now + worker.getId()) % 24 != 0) {
            return;
        }

        final AABB scanBox = worker.getBoundingBox().inflate(SWARM_SCAN_RADIUS, 8.0D, SWARM_SCAN_RADIUS);
        final List<LivingEntity> hostiles = level.getEntitiesOfClass(
                LivingEntity.class,
                scanBox,
                entity -> entity != null
                        && entity.isAlive()
                        && (entity instanceof AbstractEntityMinecoloniesRaider
                            || AbstractEntityAIGuard.isAttackableTarget(worker, entity)));

        final boolean isRaidActive = colony.getRaiderManager() != null && colony.getRaiderManager().isRaided();
        final int swarmThreshold = AdvancedColoniesConfig.CITIZEN_COMMON.guardSwarmMobThreshold.get();

        if (hostiles.isEmpty() || (hostiles.size() < swarmThreshold && !isRaidActive)) {
            return;
        }

        final LivingEntity primaryTarget = hostiles.get(0);
        final BlockPos hotspotPos = primaryTarget.blockPosition();
        ACTIVE_HOTSPOTS_BY_COLONY.put(
                colony.getID(),
                new ThreatHotspot(hotspotPos, new WeakReference<>(primaryTarget), hostiles.size(), now));

        rallyNearbyGuardsToSwarm(colony, worker, hotspotPos, hostiles);
    }

    /**
     * Alerts nearby non-engaged Guards in the colony to converge on the detected mob swarm or raid hotspot.
     */
    private static void rallyNearbyGuardsToSwarm(
            @NotNull final IColony colony,
            @NotNull final AbstractEntityCitizen callingGuard,
            @NotNull final BlockPos hotspotPos,
            @NotNull final List<LivingEntity> hostiles) {

        final int radius = AdvancedColoniesConfig.CITIZEN_COMMON.guardSwarmReinforcementRadius.get();
        final double maxDistSqr = (double) radius * (double) radius;

        int hostileIndex = 0;
        for (final ICitizenData citizenData : colony.getCitizenManager().getCitizens()) {
            if (citizenData.getId() == callingGuard.getCitizenData().getId()) {
                continue;
            }
            if (!(citizenData.getJob() instanceof AbstractJobGuard<?> fellowGuardJob)) {
                continue;
            }

            final Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
            if (entityOpt.isEmpty() || !(entityOpt.get() instanceof EntityCitizen fellowEntity)) {
                continue;
            }

            final double distSqr = fellowEntity.blockPosition().distSqr(hotspotPos);
            if (distSqr > maxDistSqr) {
                continue;
            }

            if (fellowGuardJob.getWorkerAI() instanceof AbstractEntityAIGuard<?, ?> fellowAi) {
                final IAIState state = fellowAi.getState();
                if (state != CombatAIStates.NO_TARGET && state != AIWorkerState.GUARD_SLEEP) {
                    continue;
                }
                if (!fellowAi.canHelp(hotspotPos)) {
                    continue;
                }

                // Assign one of the swarm's hostiles to the fellow guard's threat table if close enough,
                // and set their patrol point directly to the swarm hotspot so the squad converges together.
                final LivingEntity assignedHostile = hostiles.get(hostileIndex % hostiles.size());
                hostileIndex++;

                final long key = composeGuardKey(colony.getID(), citizenData.getId());
                ACTIVE_GUARD_PATROL_TARGETS.put(key, hotspotPos);

                if (!fellowEntity.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                    fellowEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false));
                }

                if (distSqr <= 36.0D * 36.0D && fellowEntity instanceof IThreatTableEntity threatEntity) {
                    threatEntity.getThreatTable().addThreat(assignedHostile, 25);
                    fellowAi.registerTarget(new AIOneTimeEventTarget(CombatAIStates.ATTACKING));
                }
            }
        }
    }

    /**
     * Computes or updates a Guard's next patrol target:
     * <ul>
     *   <li>During an active Raid or Mob Swarm hotspot, converges Guards as a tactical squad around the threat.</li>
     *   <li>During peaceful operation, disperses Guards across the colony to maximize spatial coverage
     *       and prevent clumping.</li>
     * </ul>
     *
     * @param worker             the Guard citizen entity
     * @param buildingGuards     the Guard's assigned building
     * @param currentPatrolPoint the Guard's current patrol point (may be {@code null})
     * @param arrivedAtCurrent   {@code true} if the Guard has just reached {@code currentPatrolPoint}
     * @return the updated patrol target {@link BlockPos}, or {@code null} to fall back to vanilla
     */
    @Nullable
    public static BlockPos selectOrUpdatePatrolPoint(
            @Nullable final AbstractEntityCitizen worker,
            @Nullable final AbstractBuildingGuards buildingGuards,
            @Nullable final BlockPos currentPatrolPoint,
            final boolean arrivedAtCurrent) {

        if (worker == null || buildingGuards == null || worker.getCitizenData() == null) {
            return currentPatrolPoint;
        }

        final IColony colony = buildingGuards.getColony();
        if (colony == null) {
            return currentPatrolPoint;
        }

        final long now = worker.level().getGameTime();
        final int citizenId = worker.getCitizenData().getId();
        final long guardKey = composeGuardKey(colony.getID(), citizenId);

        if (arrivedAtCurrent && currentPatrolPoint != null) {
            recordPatrolVisit(colony, currentPatrolPoint, now);
        }

        // 1. Check if a Tactical Group Response (Mob Swarm or Active Colony Raid) overrides normal dispersion
        if (AdvancedColoniesConfig.CITIZEN_COMMON.enableTacticalGroupResponseOnSwarmsAndRaids.get()) {
            final BlockPos tacticalGroupTarget = findTacticalGroupTarget(colony, worker, buildingGuards, citizenId, now);
            if (tacticalGroupTarget != null) {
                // If not yet at the tactical group target, or if the current target is far from the hotspot, redirect immediately
                if (currentPatrolPoint == null
                        || arrivedAtCurrent
                        || currentPatrolPoint.distSqr(tacticalGroupTarget) > 36.0D) {
                    ACTIVE_GUARD_PATROL_TARGETS.put(guardKey, tacticalGroupTarget);
                    if (!worker.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                        worker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 0, false, false));
                    }
                    return tacticalGroupTarget;
                }
                return currentPatrolPoint;
            }
        }

        // 2. Peaceful Patrol Dispersion
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableSmartGuardPatrolDispersion.get()) {
            return null;
        }

        // Keep walking to the Guard's individual patrol target until reached
        if (currentPatrolPoint != null && !arrivedAtCurrent) {
            ACTIVE_GUARD_PATROL_TARGETS.put(guardKey, currentPatrolPoint);
            return currentPatrolPoint;
        }

        // Select a new dispersed patrol target for this specific Guard
        final BlockPos nextTarget;
        if (buildingGuards.shallPatrolManually()) {
            // Advance the manual waypoint list per-guard so multiple guards in the same tower stagger along the route
            nextTarget = buildingGuards.getNextPatrolTarget(true);
        } else {
            nextTarget = pickDispersedColonyBuildingTarget(colony, worker, buildingGuards, citizenId, now);
        }

        if (nextTarget != null) {
            ACTIVE_GUARD_PATROL_TARGETS.put(guardKey, nextTarget);
        }
        return nextTarget;
    }

    /**
     * Finds a tactical squad convergence point if a mob swarm hotspot or active colony raid is nearby.
     */
    @Nullable
    private static BlockPos findTacticalGroupTarget(
            @NotNull final IColony colony,
            @NotNull final AbstractEntityCitizen worker,
            @NotNull final AbstractBuildingGuards buildingGuards,
            final int citizenId,
            final long now) {

        final int radius = AdvancedColoniesConfig.CITIZEN_COMMON.guardSwarmReinforcementRadius.get();
        final double maxDistSqr = (double) radius * (double) radius;

        // A. Check active mob-swarm combat hotspot
        final ThreatHotspot hotspot = ACTIVE_HOTSPOTS_BY_COLONY.get(colony.getID());
        if (hotspot != null) {
            final LivingEntity threat = hotspot.primaryThreat().get();
            if ((now - hotspot.timestampTick()) <= HOTSPOT_EXPIRY_TICKS && (threat == null || threat.isAlive())) {
                final BlockPos targetPos = threat != null ? threat.blockPosition() : hotspot.position();
                if (worker.blockPosition().distSqr(targetPos) <= maxDistSqr) {
                    return offsetSquadPosition(targetPos, citizenId);
                }
            } else {
                ACTIVE_HOTSPOTS_BY_COLONY.remove(colony.getID(), hotspot);
            }
        }

        // B. Check active colony raid: group up with fellow guards near the active raid front
        if (colony.getRaiderManager() != null && colony.getRaiderManager().isRaided()) {
            // First check if any fellow guard in the colony is actively fighting raiders right now
            for (final ICitizenData data : colony.getCitizenManager().getCitizens()) {
                if (data.getId() == citizenId || !(data.getJob() instanceof AbstractJobGuard<?> guardJob)) {
                    continue;
                }
                final Optional<AbstractEntityCitizen> entityOpt = data.getEntity();
                if (entityOpt.isPresent()
                        && guardJob.getWorkerAI() instanceof AbstractEntityAIGuard<?, ?> fellowAi
                        && fellowAi.getState() == CombatAIStates.ATTACKING) {
                    final BlockPos combatPos = entityOpt.get().blockPosition();
                    if (worker.blockPosition().distSqr(combatPos) <= maxDistSqr) {
                        return offsetSquadPosition(combatPos, citizenId);
                    }
                }
            }

            // Otherwise converge on the nearest raid spawn point within patrol/reinforcement range
            final List<BlockPos> spawnPoints = colony.getRaiderManager().getLastSpawnPoints();
            if (spawnPoints != null && !spawnPoints.isEmpty()) {
                BlockPos closestRaidPoint = null;
                double bestDistSqr = Double.MAX_VALUE;
                for (final BlockPos raidPos : spawnPoints) {
                    if (raidPos != null) {
                        final double dSqr = worker.blockPosition().distSqr(raidPos);
                        if (dSqr < bestDistSqr && dSqr <= Math.max(maxDistSqr, 120.0D * 120.0D)) {
                            bestDistSqr = dSqr;
                            closestRaidPoint = raidPos;
                        }
                    }
                }
                if (closestRaidPoint != null) {
                    return offsetSquadPosition(closestRaidPoint, citizenId);
                }
            }
        }

        return null;
    }

    /**
     * Applies a small deterministic 3x3 tactical squad offset around a convergence point so
     * grouped guards stand shoulder-to-shoulder instead of clipping into the exact same 1x1 block.
     */
    @NotNull
    private static BlockPos offsetSquadPosition(@NotNull final BlockPos center, final int citizenId) {
        final int slot = Math.floorMod(citizenId, 8);
        final int dx = switch (slot) {
            case 0, 1, 2 -> -2 + slot * 2;
            case 3 -> 2;
            case 4, 5, 6 -> 2 - (slot - 4) * 2;
            default -> -2;
        };
        final int dz = switch (slot) {
            case 0, 1, 2 -> -2;
            case 3, 7 -> 0;
            default -> 2;
        };
        return center.offset(dx, 0, dz);
    }

    /**
     * Picks the best building in the colony for peaceful patrolling by combining:
     * <ol>
     *   <li>Time since the building was last patrolled by any Guard (coverage need),</li>
     *   <li>Priority weight for residential/social buildings (happiness synergy),</li>
     *   <li>Repulsion penalty from other Guards' current positions and active patrol targets (anti-blob dispersion).</li>
     * </ol>
     */
    @Nullable
    private static BlockPos pickDispersedColonyBuildingTarget(
            @NotNull final IColony colony,
            @NotNull final AbstractEntityCitizen worker,
            @NotNull final AbstractBuildingGuards buildingGuards,
            final int citizenId,
            final long now) {

        final Map<BlockPos, IBuilding> buildingsMap = colony.getServerBuildingManager().getBuildings();
        if (buildingsMap == null || buildingsMap.isEmpty()) {
            return buildingGuards.getPosition();
        }

        final BlockPos towerPos = buildingGuards.getPosition();
        final double maxPatrolDist = buildingGuards.getPatrolDistance();
        final int minSep = AdvancedColoniesConfig.CITIZEN_COMMON.guardPatrolMinSeparationBlocks.get();
        final double minSepSqr = (double) minSep * (double) minSep;

        // Collect all other active Guards' current positions and target positions in this colony
        final List<BlockPos> otherGuardLocations = new ArrayList<>();
        for (final ICitizenData data : colony.getCitizenManager().getCitizens()) {
            if (data.getId() == citizenId || !(data.getJob() instanceof AbstractJobGuard<?>)) {
                continue;
            }
            data.getEntity().ifPresent(e -> otherGuardLocations.add(e.blockPosition()));
            final BlockPos target = ACTIVE_GUARD_PATROL_TARGETS.get(composeGuardKey(colony.getID(), data.getId()));
            if (target != null) {
                otherGuardLocations.add(target);
            }
        }

        BlockPos bestBuildingPos = null;
        double bestScore = -Double.MAX_VALUE;

        for (final IBuilding candidate : buildingsMap.values()) {
            if (candidate == null || candidate.getBuildingLevel() < 1) {
                continue;
            }
            final BlockPos pos = candidate.getPosition();
            if (BlockPosUtil.getDistance(pos, towerPos) > maxPatrolDist) {
                continue;
            }

            // Avoid picking the exact building the guard is already standing at if alternatives exist
            if (worker.blockPosition().distSqr(pos) < 16.0D && buildingsMap.size() > 1) {
                continue;
            }

            final long lastVisited = BUILDING_LAST_PATROLLED_TICK.getOrDefault(pos, 0L);
            final long ticksSinceVisit = Math.min(COVERAGE_WINDOW_TICKS, Math.max(0L, now - lastVisited));

            // Base score from how long this building has been unpatrolled (0 .. 120 points)
            double score = ticksSinceVisit / 100.0D;

            // Prioritize residential & citizen-gathering buildings (Residences, Tavern, Hospital, TownHall)
            if (candidate.hasModule(LivingBuildingModule.class)) {
                score *= 1.35D;
            }

            // Apply anti-clumping repulsion if any other Guard is already near or heading to this building
            for (final BlockPos otherPos : otherGuardLocations) {
                final double distSqr = pos.distSqr(otherPos);
                if (distSqr < 9.0D) {
                    // Another guard is targeting this exact building
                    score -= 150.0D;
                } else if (distSqr < minSepSqr) {
                    final double ratio = 1.0D - (Math.sqrt(distSqr) / minSep);
                    score -= 80.0D * ratio;
                }
            }

            // Add small deterministic per-guard jitter so two guards never tie on the exact same building
            final int hash = Math.floorMod(pos.hashCode() ^ (citizenId * 31 + (int) (now / 100L)), 15);
            score += hash;

            if (score > bestScore) {
                bestScore = score;
                bestBuildingPos = pos;
            }
        }

        return bestBuildingPos != null ? bestBuildingPos : buildingGuards.getPosition();
    }

    /**
     * Records that a Guard has arrived at a patrol position, updating the coverage timestamp
     * for the nearest colony building.
     */
    private static void recordPatrolVisit(
            @NotNull final IColony colony,
            @NotNull final BlockPos patrolPos,
            final long now) {

        BUILDING_LAST_PATROLLED_TICK.put(patrolPos, now);
        final Map<BlockPos, IBuilding> buildings = colony.getServerBuildingManager().getBuildings();
        if (buildings == null) {
            return;
        }
        for (final IBuilding building : buildings.values()) {
            if (building != null && building.getPosition().distSqr(patrolPos) <= 64.0D) {
                BUILDING_LAST_PATROLLED_TICK.put(building.getPosition(), now);
            }
        }
    }

    /**
     * Enhances the colony's Security happiness factor ({@code CitizenHappinessHandler.getGuardFactor})
     * with a spatial coverage bonus when Guards are well-distributed across the colony's buildings.
     *
     * @param colony            the colony being evaluated
     * @param vanillaGuardFactor the base guard count factor computed by MineColonies
     * @return the coverage-adjusted guard happiness factor (capped at {@code 2.0})
     */
    public static double computeSecurityFactorWithCoverageBonus(
            @Nullable final IColony colony,
            final double vanillaGuardFactor) {

        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        if (!cfg.enableGuardCoverageHappinessBonus.get()
                || colony == null
                || colony.getWorld() == null
                || vanillaGuardFactor <= 0.0D) {
            return vanillaGuardFactor;
        }

        final Map<BlockPos, IBuilding> buildingsMap = colony.getServerBuildingManager() != null
                ? colony.getServerBuildingManager().getBuildings()
                : null;
        if (buildingsMap == null || buildingsMap.isEmpty()) {
            return vanillaGuardFactor;
        }

        final long now = colony.getWorld().getGameTime();

        // 1. Calculate fraction of constructed buildings patrolled within COVERAGE_WINDOW_TICKS
        int totalConstructed = 0;
        int recentlyPatrolled = 0;
        final Collection<IBuilding> buildings = buildingsMap.values();
        for (final IBuilding building : buildings) {
            if (building != null && building.getBuildingLevel() >= 1) {
                totalConstructed++;
                final Long lastVisit = BUILDING_LAST_PATROLLED_TICK.get(building.getPosition());
                if (lastVisit != null && (now - lastVisit) <= COVERAGE_WINDOW_TICKS) {
                    recentlyPatrolled++;
                }
            }
        }

        final double buildingCoverageRatio = totalConstructed > 0
                ? (double) recentlyPatrolled / totalConstructed
                : 0.0D;

        // 2. Calculate live spatial dispersion among active Guards
        final List<BlockPos> guardPositions = new ArrayList<>();
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            if (citizen.getJob() instanceof AbstractJobGuard<?>) {
                citizen.getEntity().ifPresent(e -> guardPositions.add(e.blockPosition()));
            }
        }

        double spatialDispersionRatio = 0.0D;
        if (guardPositions.size() >= 2) {
            final int minSep = cfg.guardPatrolMinSeparationBlocks.get();
            final double minSepSqr = (double) minSep * (double) minSep;
            int wellSeparatedPairs = 0;
            int totalPairs = 0;
            for (int i = 0; i < guardPositions.size(); i++) {
                for (int j = i + 1; j < guardPositions.size(); j++) {
                    totalPairs++;
                    if (guardPositions.get(i).distSqr(guardPositions.get(j)) >= minSepSqr) {
                        wellSeparatedPairs++;
                    }
                }
            }
            spatialDispersionRatio = totalPairs > 0 ? (double) wellSeparatedPairs / totalPairs : 0.0D;
        } else if (guardPositions.size() == 1) {
            spatialDispersionRatio = buildingCoverageRatio;
        }

        final double combinedCoverage = 0.6D * buildingCoverageRatio + 0.4D * spatialDispersionRatio;
        final double bonusMultiplier = 1.0D + (combinedCoverage * MAX_COVERAGE_HAPPINESS_BONUS);
        return Math.min(2.0D, vanillaGuardFactor * bonusMultiplier);
    }

    private static long composeGuardKey(final int colonyId, final int citizenId) {
        return ((long) colonyId << 32) | (citizenId & 0xFFFFFFFFL);
    }
}
