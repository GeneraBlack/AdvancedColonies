package com.advancedcolonies.workers.guard;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.CitizenCommonConfig;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

/**
 * Domain helper for Guard AI combat readiness, harmless wake-up, and low-health retreat:
 * <ul>
 *   <li>Prevents Guards from falling asleep on duty while a colony raid/attack is active (or completely if configured).</li>
 *   <li>Wakes up sleeping fellow Guards cleanly without dealing real HP damage or armor durability loss.</li>
 *   <li>Honors the Guard Tower's "Retreat on low health" setting with accurate floating-point health checks even before University Retreat research is unlocked.</li>
 * </ul>
 */
public final class GuardCombatAndDutyHelper {

    /** Low-health threshold fraction (20% of max health) at which a Guard retreats when enabled. */
    private static final float RETREAT_HEALTH_FRACTION = 0.20F;

    /** Minimum squared distance from the Guard's home tower required to trigger a retreat path. */
    private static final double MIN_RETREAT_DIST_SQR = 20.0D;

    private GuardCombatAndDutyHelper() {
    }

    /**
     * Checks whether a Guard should be prevented from falling asleep on duty.
     *
     * @param worker the Guard citizen entity
     * @return {@code true} if sleeping on duty should be blocked
     */
    public static boolean shouldPreventGuardSleeping(@Nullable final AbstractEntityCitizen worker) {
        if (worker == null) {
            return false;
        }

        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        if (cfg.disableGuardSleepingOnDuty.get()) {
            return true;
        }

        if (cfg.preventGuardSleepingDuringRaids.get() && worker.getCitizenColonyHandler() != null) {
            final IColony colony = worker.getCitizenColonyHandler().getColonyOrRegister();
            if (colony != null && colony.getRaiderManager() != null && colony.getRaiderManager().isRaided()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Wakes up a sleeping Guard without dealing real HP damage or armor durability loss when
     * {@code harmlessGuardWakeUp} is enabled.
     *
     * @param wakingWorker    the Guard performing the wake-up action
     * @param sleepingCitizen the sleeping Guard being woken up
     * @return {@code true} if the harmless wake-up was handled
     */
    public static boolean tryHarmlessWakeUpGuard(
            @Nullable final AbstractEntityCitizen wakingWorker,
            @Nullable final EntityCitizen sleepingCitizen) {

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.harmlessGuardWakeUp.get()
                || wakingWorker == null
                || sleepingCitizen == null) {
            return false;
        }

        wakingWorker.swing(InteractionHand.OFF_HAND);
        // Setting lastHurtByMob triggers sleep() to immediately call stopSleeping() and clear lastHurtByMob
        // on the sleeping guard's next tick without inflicting actual HP damage or armor durability loss.
        sleepingCitizen.setLastHurtByMob(wakingWorker);
        sleepingCitizen.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.75F, 1.1F);
        return true;
    }

    /**
     * Evaluates whether a Guard should retreat to their Guard Tower on low health when
     * {@code honorGuardRetreatWithoutResearch} is enabled.
     *
     * @param worker         the Guard citizen entity
     * @param buildingGuards the Guard's assigned building
     * @return {@code true} if the Guard should flee to regenerate
     */
    public static boolean shouldGuardRetreatOnLowHealth(
            @Nullable final AbstractEntityCitizen worker,
            @Nullable final IGuardBuilding buildingGuards) {

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.honorGuardRetreatWithoutResearch.get()
                || worker == null
                || buildingGuards == null) {
            return false;
        }

        if (!buildingGuards.shallRetrieveOnLowHealth()) {
            return false;
        }

        if (worker.getHealth() >= worker.getMaxHealth() * RETREAT_HEALTH_FRACTION) {
            return false;
        }

        return buildingGuards.getID() != null
                && worker.distanceToSqr(buildingGuards.getID().getCenter()) > MIN_RETREAT_DIST_SQR;
    }

    /**
     * Checks whether Raider threat and aggro rebalancing is enabled in the server configuration.
     *
     * @return {@code true} if enabled
     */
    public static boolean isRaiderThreatRebalancingEnabled() {
        return AdvancedColoniesConfig.CITIZEN_COMMON.enableRaiderThreatRebalancing.get();
    }

    /**
     * Evaluates a Raider's current target: drops Creative/Spectator players and caps threat on
     * retreating low-health Guards ({@code GUARD_FLEE}) to the server limit {@code raiderFleeingGuardMaxThreat}.
     *
     * @param raider        the Raider entity
     * @param currentTarget the Raider's current combat target
     * @param <T>           the Raider entity type
     * @return {@code false} if the current target is invalid (Creative/Spectator) and should be reset
     */
    public static <T extends com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster & com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity> boolean evaluateRaiderCurrentTarget(
            @Nullable final T raider,
            @Nullable final net.minecraft.world.entity.LivingEntity currentTarget) {

        if (!isRaiderThreatRebalancingEnabled() || raider == null || currentTarget == null || !currentTarget.isAlive()) {
            return true;
        }

        if (currentTarget instanceof net.minecraft.world.entity.player.Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
        } else if (currentTarget instanceof AbstractEntityCitizen citizen
                && citizen.getCitizenJobHandler() != null
                && citizen.getCitizenJobHandler().getColonyJob() instanceof com.minecolonies.core.colony.jobs.AbstractJobGuard<?> guard
                && guard.getWorkerAI() != null
                && guard.getWorkerAI().getState() == com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.GUARD_FLEE) {

            final int maxFleeThreat = Math.max(0, Math.min(100,
                    AdvancedColoniesConfig.CITIZEN_COMMON.raiderFleeingGuardMaxThreat.get()));
            final int currentThreat = raider.getThreatTable().getThreatFor(currentTarget);
            if (currentThreat > maxFleeThreat) {
                raider.getThreatTable().addThreat(currentTarget, maxFleeThreat - currentThreat);
            }
        }

        return true;
    }

    /**
     * Boosts threat on active (non-fleeing) Guards within the Raider's search area according to
     * the server-configured {@code raiderActiveGuardThreatBonus} limit.
     *
     * @param raider     the Raider entity
     * @param searchArea the Raider's target search bounding box
     * @param <T>        the Raider entity type
     */
    public static <T extends com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster & com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity> void applyRaiderNearbyGuardThreat(
            @Nullable final T raider,
            @Nullable final net.minecraft.world.phys.AABB searchArea) {

        if (!isRaiderThreatRebalancingEnabled() || raider == null || searchArea == null) {
            return;
        }

        final int activeGuardBonus = Math.max(0, Math.min(200,
                AdvancedColoniesConfig.CITIZEN_COMMON.raiderActiveGuardThreatBonus.get()));
        if (activeGuardBonus <= 0) {
            return;
        }

        final java.util.List<net.minecraft.world.entity.LivingEntity> entities =
                raider.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, searchArea);
        for (final net.minecraft.world.entity.LivingEntity entity : entities) {
            if (entity instanceof AbstractEntityCitizen citizen
                    && citizen.isAlive()
                    && !citizen.isInvisible()
                    && citizen.getCitizenJobHandler() != null
                    && citizen.getCitizenJobHandler().getColonyJob() instanceof com.minecolonies.core.colony.jobs.AbstractJobGuard<?> guard) {

                if (guard.getWorkerAI() != null
                        && guard.getWorkerAI().getState() != com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.GUARD_FLEE) {
                    raider.getThreatTable().addThreat(entity, activeGuardBonus);
                }
            }
        }
    }

    /**
     * Checks whether an entity is a valid attackable target for a Raider when threat rebalancing is enabled.
     *
     * @param target the candidate target entity
     * @return {@code true} if the target is attackable
     */
    public static boolean isRaiderAttackableTarget(@Nullable final net.minecraft.world.entity.LivingEntity target) {
        if (target == null) {
            return false;
        }
        if (target instanceof AbstractEntityCitizen) {
            return !target.isInvisible();
        }
        if (target instanceof net.minecraft.world.entity.player.Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return false;
    }
}

