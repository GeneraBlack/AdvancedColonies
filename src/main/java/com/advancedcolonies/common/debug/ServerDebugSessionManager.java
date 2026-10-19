package com.advancedcolonies.common.debug;

import com.advancedcolonies.AdvancedColonies;
import com.advancedcolonies.common.navigation.NavigationStuckAndRescueHelper;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.network.CitizenDebugSyncPayload;
import com.advancedcolonies.workers.builder.BuilderClearingStateAccess;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.entity.ai.ITickingStateAI;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.PathPointExtended;
import com.minecolonies.core.entity.pathfinding.navigation.AbstractAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player visual debug sessions on the logical server, collects live
 * {@link CitizenDebugSnapshot}s (including 3D movement/stall tracking, Builder progress,
 * open requests, and inventory telemetry), and dispatches {@link CitizenDebugSyncPayload} packets.
 */
@EventBusSubscriber(modid = AdvancedColonies.MOD_ID)
public final class ServerDebugSessionManager {

    /** Per-player active debug configuration. */
    public static final class PlayerDebugSession {
        private final Set<Integer> watchedColonies = new HashSet<>();
        private final Map<Integer, Set<Integer>> watchedCitizensByColony = new HashMap<>();
        private boolean showActions = true;
        private boolean showStats = true;
        private boolean showPathfinding = true;
        @Nullable
        private String jobFilter = null;
        private boolean stuckOnlyFilter = false;

        public Set<Integer> getWatchedColonies() {
            return Collections.unmodifiableSet(this.watchedColonies);
        }

        public Map<Integer, Set<Integer>> getWatchedCitizensByColony() {
            return Collections.unmodifiableMap(this.watchedCitizensByColony);
        }

        public boolean isShowActions() {
            return this.showActions;
        }

        public void setShowActions(final boolean showActions) {
            this.showActions = showActions;
        }

        public boolean isShowStats() {
            return this.showStats;
        }

        public void setShowStats(final boolean showStats) {
            this.showStats = showStats;
        }

        public boolean isShowPathfinding() {
            return this.showPathfinding;
        }

        public void setShowPathfinding(final boolean showPathfinding) {
            this.showPathfinding = showPathfinding;
        }

        @Nullable
        public String getJobFilter() {
            return this.jobFilter;
        }

        public void setJobFilter(@Nullable final String jobFilter) {
            this.jobFilter = jobFilter;
        }

        public boolean isStuckOnlyFilter() {
            return this.stuckOnlyFilter;
        }

        public void setStuckOnlyFilter(final boolean stuckOnlyFilter) {
            this.stuckOnlyFilter = stuckOnlyFilter;
        }

        public boolean isEmpty() {
            return this.watchedColonies.isEmpty() && this.watchedCitizensByColony.isEmpty();
        }

        public void clear() {
            this.watchedColonies.clear();
            this.watchedCitizensByColony.clear();
            this.jobFilter = null;
            this.stuckOnlyFilter = false;
        }
    }

    /** Live stuck metrics reported by {@code PathingStuckHandlerMixin}. */
    public record StuckMetrics(int stuckLevel, int globalTimeoutTicks, long gameTime) {
    }

    private static final Map<UUID, PlayerDebugSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, StuckMetrics> STUCK_METRICS = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    private ServerDebugSessionManager() {
    }

    /**
     * Records live stuck metrics for a citizen entity from {@code PathingStuckHandler}.
     *
     * @param entityId           the runtime entity ID
     * @param stuckLevel         the current stuck level
     * @param globalTimeoutTicks the current global timeout ticks
     * @param gameTime           the server level game time
     */
    public static void recordStuckMetrics(
            final int entityId,
            final int stuckLevel,
            final int globalTimeoutTicks,
            final long gameTime) {
        STUCK_METRICS.put(entityId, new StuckMetrics(stuckLevel, globalTimeoutTicks, gameTime));
    }

    /**
     * Returns or creates the debug session for a player.
     */
    @NotNull
    public static PlayerDebugSession getOrCreateSession(@NotNull final ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), k -> new PlayerDebugSession());
    }

    /**
     * Returns the existing debug session for a player, or {@code null} if inactive.
     */
    @Nullable
    public static PlayerDebugSession getSession(@NotNull final ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    /**
     * Sets or toggles colony-wide visual debugging for a player.
     *
     * @param player   the player
     * @param colonyId the target colony ID
     * @param enable   {@code true} to enable, {@code false} to disable, or {@code null} to toggle
     * @return {@code true} if colony debugging is now enabled, {@code false} if disabled
     */
    public static boolean setColonyDebug(
            @NotNull final ServerPlayer player,
            final int colonyId,
            @Nullable final Boolean enable) {
        final PlayerDebugSession session = getOrCreateSession(player);
        final boolean currentlyEnabled = session.watchedColonies.contains(colonyId)
                && session.jobFilter == null
                && !session.stuckOnlyFilter;
        final boolean targetState = enable != null ? enable : !currentlyEnabled;
        if (targetState) {
            session.watchedColonies.add(colonyId);
            session.jobFilter = null;
            session.stuckOnlyFilter = false;
        } else {
            session.watchedColonies.remove(colonyId);
            session.jobFilter = null;
            session.stuckOnlyFilter = false;
        }
        cleanupAndSync(player, session);
        return targetState;
    }

    /**
     * Enables colony-wide visual debugging filtered to citizens matching {@code jobFilter}.
     */
    public static void setColonyJobFilter(
            @NotNull final ServerPlayer player,
            final int colonyId,
            @Nullable final String jobFilter) {
        final PlayerDebugSession session = getOrCreateSession(player);
        session.watchedColonies.add(colonyId);
        final String clean = jobFilter == null ? "" : jobFilter.trim().toLowerCase(Locale.ROOT);
        session.jobFilter = (clean.isEmpty() || "all".equals(clean) || "*".equals(clean)) ? null : clean;
        session.stuckOnlyFilter = false;
        cleanupAndSync(player, session);
    }

    /**
     * Enables colony-wide visual debugging filtered to genuinely stuck/stalled citizens.
     */
    public static void setColonyStuckOnlyFilter(
            @NotNull final ServerPlayer player,
            final int colonyId,
            final boolean stuckOnly) {
        final PlayerDebugSession session = getOrCreateSession(player);
        session.watchedColonies.add(colonyId);
        session.stuckOnlyFilter = stuckOnly;
        if (stuckOnly) {
            session.jobFilter = null;
        }
        cleanupAndSync(player, session);
    }

    /**
     * Sets or toggles per-citizen visual debugging for a player.
     *
     * @param player    the player
     * @param colonyId  the citizen's colony ID
     * @param citizenId the citizen's civilian ID
     * @param enable    {@code true} to enable, {@code false} to disable, or {@code null} to toggle
     * @return {@code true} if debugging for this citizen is now enabled, {@code false} if disabled
     */
    public static boolean setCitizenDebug(
            @NotNull final ServerPlayer player,
            final int colonyId,
            final int citizenId,
            @Nullable final Boolean enable) {
        final PlayerDebugSession session = getOrCreateSession(player);
        final Set<Integer> citizens = session.watchedCitizensByColony.computeIfAbsent(colonyId, k -> new HashSet<>());
        final boolean currentlyEnabled = citizens.contains(citizenId);
        final boolean targetState = enable != null ? enable : !currentlyEnabled;
        if (targetState) {
            citizens.add(citizenId);
        } else {
            citizens.remove(citizenId);
            if (citizens.isEmpty()) {
                session.watchedCitizensByColony.remove(colonyId);
            }
        }
        cleanupAndSync(player, session);
        return targetState;
    }

    /**
     * Clears all active visual debugging targets for a player and sends an immediate clear packet.
     */
    public static void clearAll(@NotNull final ServerPlayer player) {
        final PlayerDebugSession session = SESSIONS.remove(player.getUUID());
        final boolean showActions = session == null || session.showActions;
        final boolean showStats = session == null || session.showStats;
        final boolean showPathfinding = session == null || session.showPathfinding;
        PacketDistributor.sendToPlayer(
                player,
                new CitizenDebugSyncPayload(true, showActions, showStats, showPathfinding, List.of()));
    }

    /**
     * Immediately synchronizes the player's current debug session to their client.
     */
    public static void cleanupAndSync(@NotNull final ServerPlayer player, @NotNull final PlayerDebugSession session) {
        if (session.isEmpty()) {
            SESSIONS.remove(player.getUUID());
            PacketDistributor.sendToPlayer(
                    player,
                    new CitizenDebugSyncPayload(
                            true,
                            session.showActions,
                            session.showStats,
                            session.showPathfinding,
                            List.of()));
        } else {
            syncPlayerSession(player, session);
        }
    }

    /**
     * Forces an immediate unstuck rescue on a citizen and resets any stuck Builder AI state.
     */
    public static boolean forceUnstuckCitizenAndAI(@NotNull final AbstractEntityCitizen citizen) {
        final ICitizenData data = citizen.getCitizenData();
        if (data != null && data.getJob() instanceof AbstractJob<?, ?> absJob) {
            final ITickingStateAI workerAI = absJob.getWorkerAI();
            if (workerAI instanceof BuilderClearingStateAccess builderAccess) {
                builderAccess.advancedColonies$resetStuckBuildState();
            }
        }
        return NavigationStuckAndRescueHelper.forceUnstuckCitizen(citizen, true);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(@NotNull final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            SESSIONS.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(@NotNull final ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableVisualDebugger.get()) {
            return;
        }

        final int interval = Math.max(1, AdvancedColoniesConfig.CITIZEN_COMMON.visualDebugSyncIntervalTicks.get());
        if (++tickCounter % interval != 0) {
            return;
        }

        for (final ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            final PlayerDebugSession session = SESSIONS.get(player.getUUID());
            if (session != null && !session.isEmpty()) {
                syncPlayerSession(player, session);
            }
        }
    }

    private static void syncPlayerSession(
            @NotNull final ServerPlayer player,
            @NotNull final PlayerDebugSession session) {
        final List<CitizenDebugSnapshot> snapshots = new ArrayList<>();
        final Set<Integer> seenCitizenKeys = new HashSet<>();
        final int maxRange = AdvancedColoniesConfig.CITIZEN_COMMON.visualDebugMaxRange.get();
        final double maxRangeSqr = (double) maxRange * (double) maxRange;

        // 1. Collect all citizens from watched colonies within maxRange (honoring jobFilter & stuckOnlyFilter)
        for (final int colonyId : session.watchedColonies) {
            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, player.level().dimension());
            if (colony == null) {
                continue;
            }
            for (final ICitizenData data : colony.getCitizenManager().getCitizens()) {
                final Optional<AbstractEntityCitizen> entityOpt = data.getEntity();
                if (entityOpt.isPresent() && entityOpt.get() instanceof EntityCitizen citizen) {
                    if (citizen.distanceToSqr(player) <= maxRangeSqr) {
                        final CitizenDebugSnapshot snap = buildSnapshot(colonyId, data, citizen);
                        if (session.jobFilter != null && !matchesJobFilter(data, snap.jobName(), session.jobFilter)) {
                            continue;
                        }
                        if (session.stuckOnlyFilter && !snap.isGenuinelyStuck()) {
                            continue;
                        }
                        seenCitizenKeys.add(citizen.getId());
                        snapshots.add(snap);
                    }
                }
            }
        }

        // 2. Collect explicitly watched individual citizens
        for (final Map.Entry<Integer, Set<Integer>> entry : session.watchedCitizensByColony.entrySet()) {
            final int colonyId = entry.getKey();
            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, player.level().dimension());
            if (colony == null) {
                continue;
            }
            for (final int citizenId : entry.getValue()) {
                final ICitizenData data = colony.getCitizenManager().getCivilian(citizenId);
                if (data == null) {
                    continue;
                }
                final Optional<AbstractEntityCitizen> entityOpt = data.getEntity();
                if (entityOpt.isPresent() && entityOpt.get() instanceof EntityCitizen citizen) {
                    if (seenCitizenKeys.add(citizen.getId())) {
                        snapshots.add(buildSnapshot(colonyId, data, citizen));
                    }
                }
            }
        }

        PacketDistributor.sendToPlayer(
                player,
                new CitizenDebugSyncPayload(
                        false,
                        session.showActions,
                        session.showStats,
                        session.showPathfinding,
                        snapshots));
    }

    private static boolean matchesJobFilter(
            @NotNull final ICitizenData data,
            @NotNull final String localizedJobName,
            @NotNull final String filter) {
        if (localizedJobName.toLowerCase(Locale.ROOT).contains(filter)) {
            return true;
        }
        final IJob<?> job = data.getJob();
        if (job != null) {
            final String classSimple = job.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (classSimple.contains(filter)) {
                return true;
            }
            if (job.getJobRegistryEntry() != null && job.getJobRegistryEntry().getKey() != null) {
                return job.getJobRegistryEntry().getKey().toString().toLowerCase(Locale.ROOT).contains(filter);
            }
        }
        return false;
    }

    /**
     * Builds a comprehensive {@link CitizenDebugSnapshot} for a single citizen entity.
     */
    @NotNull
    public static CitizenDebugSnapshot buildSnapshot(
            final int colonyId,
            @NotNull final ICitizenData data,
            @NotNull final EntityCitizen citizen) {
        final String name = data.getName();
        String jobName = "Unemployed";
        String workerAiState = "None";

        final IJob<?> job = data.getJob();
        if (job != null) {
            jobName = Component.translatable(job.getJobRegistryEntry().getTranslationKey()).getString();
            final ITickingStateAI workAi = job.getWorkerAI();
            if (workAi != null && workAi.getStateAI() != null && workAi.getStateAI().getState() != null) {
                workerAiState = workAi.getStateAI().getState().toString();
            }
        }

        final IState topState = citizen.getCitizenAI() != null ? citizen.getCitizenAI().getState() : null;
        final String citizenAiState = topState != null ? topState.toString() : "IDLE";

        final float health = citizen.getHealth();
        final float maxHealth = citizen.getMaxHealth();
        final double saturation = data.getSaturation();
        final double happiness = data.getCitizenHappinessHandler() != null
                ? data.getCitizenHappinessHandler().getHappiness(data.getColony(), data)
                : 0.0D;

        // Real-time 3D movement & stall telemetry from NavigationStuckAndRescueHelper
        final NavigationStuckAndRescueHelper.CitizenMovementTracker moveTracker =
                NavigationStuckAndRescueHelper.getTracker(citizen.getId());
        final StuckMetrics stuck = STUCK_METRICS.get(citizen.getId());
        final long now = citizen.level().getGameTime();
        final int metricStuckLevel = (stuck != null && (now - stuck.gameTime()) < 100L) ? stuck.stuckLevel() : 0;
        final int metricTimeout = (stuck != null && (now - stuck.gameTime()) < 100L) ? stuck.globalTimeoutTicks() : 0;

        final int stuckLevel = moveTracker != null
                ? Math.max(moveTracker.getVanillaStuckLevel(), metricStuckLevel)
                : metricStuckLevel;
        final int globalTimeout = moveTracker != null
                ? Math.max(moveTracker.getVanillaGlobalTimeout(), metricTimeout)
                : metricTimeout;
        final int realStalledTicks = moveTracker != null ? moveTracker.getRealStalledTicks() : 0;
        final float moveSpeedBps = moveTracker != null ? moveTracker.getMoveSpeedBps() : 0.0F;
        final String rescueAction = moveTracker != null ? moveTracker.getLastRescueAction() : "NONE";

        // Work building info
        String workBuildingInfo = "None";
        BlockPos workBuildingPos = null;
        final IBuilding wb = data.getWorkBuilding();
        if (wb != null) {
            workBuildingPos = wb.getPosition();
            final String schem = wb.getSchematicName();
            final String cleanSchem = (schem == null || schem.isEmpty())
                    ? wb.getClass().getSimpleName().replace("Building", "")
                    : schem.substring(0, 1).toUpperCase(Locale.ROOT) + schem.substring(1);
            workBuildingInfo = cleanSchem + " Lv." + wb.getBuildingLevel();
        }

        // Held item & inventory slots
        final ItemStack mainHand = citizen.getMainHandItem();
        final String heldItemName = mainHand.isEmpty()
                ? "Empty"
                : (mainHand.getCount() > 1 ? mainHand.getCount() + "x " : "") + truncate(mainHand.getHoverName().getString(), 26);

        int usedSlots = 0;
        int totalSlots = 0;
        final InventoryCitizen inv = data.getInventory();
        if (inv != null) {
            totalSlots = inv.getSlots();
            for (int i = 0; i < totalSlots; i++) {
                if (!inv.getStackInSlot(i).isEmpty()) {
                    usedSlots++;
                }
            }
        }

        // Builder / Structure AI telemetry
        String builderStageInfo = "";
        BlockPos workTargetPos = null;
        BlockPos workStandPos = null;
        if (job instanceof AbstractJob<?, ?> absJob) {
            final ITickingStateAI workerAI = absJob.getWorkerAI();
            if (workerAI instanceof BuilderClearingStateAccess structAccess) {
                workTargetPos = structAccess.advancedColonies$getCurrentWorkTargetPos();
                workStandPos = structAccess.advancedColonies$getWorkFromPos();
                final String stageDesc = structAccess.advancedColonies$getStageDescription();
                String orderDesc = "";
                if (wb instanceof AbstractBuildingStructureBuilder sb && sb.getWorkOrder() != null) {
                    final IBuilderWorkOrder wo = sb.getWorkOrder();
                    orderDesc = truncate(wo.getDisplayName().getString(), 22) + " [" + wo.getWorkOrderType().name() + "]";
                }
                if (!stageDesc.isEmpty() && !orderDesc.isEmpty()) {
                    builderStageInfo = stageDesc + " | " + orderDesc;
                } else if (!stageDesc.isEmpty()) {
                    builderStageInfo = stageDesc;
                } else if (!orderDesc.isEmpty()) {
                    builderStageInfo = orderDesc;
                }
            }
        }
        if (workTargetPos == null && data.getStatusPosition() != null) {
            workTargetPos = data.getStatusPosition();
        }

        // Active requests & citizen complaints/status
        final String activeRequestSummary = extractActiveRequestSummary(data, wb);

        // Pathfinding route & nodes
        String pathStatus = "IDLE";
        BlockPos targetPos = null;
        int currentNodeIndex = 0;
        final List<CitizenDebugSnapshot.PathNodeEntry> nodes = new ArrayList<>();

        final PathNavigation nav = citizen.getNavigation();
        if (nav != null) {
            targetPos = nav.getTargetPos();
            if (nav instanceof AbstractAdvancedPathNavigate advNav) {
                final PathResult<?> result = advNav.getPathResult();
                if (result != null && result.getStatus() != null) {
                    pathStatus = result.getStatus().name();
                } else if (!nav.isDone()) {
                    pathStatus = "MOVING";
                }
                if (targetPos == null || BlockPos.ZERO.equals(targetPos)) {
                    final BlockPos safeDest = (advNav instanceof com.minecolonies.api.entity.pathfinding.IMinecoloniesNavigator mcNav)
                            ? mcNav.getSafeDestination()
                            : null;
                    if (safeDest != null && !BlockPos.ZERO.equals(safeDest)) {
                        targetPos = safeDest;
                    }
                }
            } else if (!nav.isDone()) {
                pathStatus = "MOVING";
            }

            final Path path = nav.getPath();
            if (path != null && path.getNodeCount() > 0) {
                currentNodeIndex = path.getNextNodeIndex();
                final int totalNodes = path.getNodeCount();
                final int startIdx = Math.max(0, currentNodeIndex - 8);
                final int endIdx = Math.min(totalNodes, startIdx + CitizenDebugSnapshot.MAX_SERIALIZED_NODES);
                for (int i = startIdx; i < endIdx; i++) {
                    final Node node = path.getNode(i);
                    boolean onLadder = false;
                    boolean onRails = false;
                    if (node instanceof PathPointExtended ext) {
                        onLadder = ext.isOnLadder();
                        onRails = ext.isOnRails();
                    }
                    final boolean reached = i < currentNodeIndex;
                    nodes.add(new CitizenDebugSnapshot.PathNodeEntry(
                            node.x,
                            node.y,
                            node.z,
                            onLadder,
                            onRails,
                            reached));
                }
                currentNodeIndex = Math.max(0, currentNodeIndex - startIdx);
            }
        }

        final BlockPos effectiveTarget = targetPos != null ? targetPos : workTargetPos;
        final float distanceToTarget = effectiveTarget != null
                ? (float) citizen.position().distanceTo(Vec3.atCenterOf(effectiveTarget))
                : 0.0F;

        return new CitizenDebugSnapshot(
                citizen.getId(),
                data.getId(),
                colonyId,
                name,
                jobName,
                citizenAiState,
                workerAiState,
                health,
                maxHealth,
                saturation,
                happiness,
                stuckLevel,
                globalTimeout,
                realStalledTicks,
                moveSpeedBps,
                distanceToTarget,
                rescueAction,
                workBuildingInfo,
                workBuildingPos,
                heldItemName,
                usedSlots,
                totalSlots,
                activeRequestSummary,
                builderStageInfo,
                workTargetPos,
                workStandPos,
                pathStatus,
                targetPos,
                currentNodeIndex,
                nodes);
    }

    @NotNull
    private static String extractActiveRequestSummary(
            @NotNull final ICitizenData data,
            @Nullable final IBuilding workBuilding) {
        try {
            if (data.getCitizenDiseaseHandler() != null && data.getCitizenDiseaseHandler().isSick()) {
                return "Status: SICK (Needs Healer)";
            }
        } catch (final Exception ignored) {
        }

        try {
            if (workBuilding != null) {
                final Collection<IRequest<?>> openReqs = workBuilding.getOpenRequests(data.getId());
                if (openReqs != null && !openReqs.isEmpty()) {
                    final IRequest<?> firstReq = openReqs.iterator().next();
                    if (firstReq != null && firstReq.getShortDisplayString() != null) {
                        final String reqText = truncate(firstReq.getShortDisplayString().getString(), 38);
                        return openReqs.size() > 1
                                ? "Req (" + openReqs.size() + "): " + reqText
                                : "Req: " + reqText;
                    }
                }
            }
        } catch (final Exception ignored) {
        }

        if (data.getSaturation() < 6.0D) {
            return String.format(Locale.ROOT, "Status: HUNGRY (%.1f/20)", data.getSaturation());
        }

        return "";
    }

    @NotNull
    private static String truncate(@NotNull final String input, final int maxLen) {
        if (input.length() <= maxLen) {
            return input;
        }
        return input.substring(0, maxLen - 1) + "…";
    }
}
