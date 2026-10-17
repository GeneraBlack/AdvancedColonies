package com.advancedcolonies.mixin.workers.guard;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.workers.guard.GuardCombatAndDutyHelper;
import com.advancedcolonies.workers.guard.GuardPatrolAndSwarmHelper;
import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link AbstractEntityAIGuard} delegating:
 * <ul>
 *   <li>Raid sleep prevention, harmless fellow-guard wake-ups, and low-health retreat checks to {@link GuardCombatAndDutyHelper}.</li>
 *   <li>Peaceful anti-blob patrol dispersion and tactical raid/swarm squad convergence to {@link GuardPatrolAndSwarmHelper}.</li>
 * </ul>
 */
@Mixin(value = AbstractEntityAIGuard.class, remap = false)
public abstract class AbstractEntityAIGuardMixin<J extends AbstractJobGuard<J>, B extends AbstractBuildingGuards>
        extends AbstractEntityAIBasic<J, B> {

    @Shadow
    @Final
    protected IGuardBuilding buildingGuards;

    @Shadow
    protected WeakReference<EntityCitizen> sleepingGuard;

    @Shadow
    protected BlockPos currentPatrolPoint;

    protected AbstractEntityAIGuardMixin(@NotNull final J job) {
        super(job);
    }

    @Inject(method = "inCombat", at = @At("HEAD"))
    private void advancedColonies$onInCombatHead(final CallbackInfoReturnable<IAIState> cir) {
        GuardPatrolAndSwarmHelper.tickCombatSwarmDetection(this.worker, this.buildingGuards);
    }

    @Inject(method = "patrol", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onPatrolHead(final CallbackInfoReturnable<IAIState> cir) {
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableSmartGuardPatrolDispersion.get()
                && !AdvancedColoniesConfig.CITIZEN_COMMON.enableTacticalGroupResponseOnSwarmsAndRaids.get()) {
            return;
        }

        final boolean arrived = this.currentPatrolPoint != null && this.walkToSafePos(this.currentPatrolPoint);
        final BlockPos nextTarget = GuardPatrolAndSwarmHelper.selectOrUpdatePatrolPoint(
                this.worker,
                this.building,
                this.currentPatrolPoint,
                arrived);

        if (nextTarget != null) {
            final boolean targetChanged = !nextTarget.equals(this.currentPatrolPoint);
            this.currentPatrolPoint = nextTarget;
            if (arrived) {
                this.setCurrentDelay(10);
            }
            if (arrived || targetChanged) {
                this.walkToSafePos(this.currentPatrolPoint);
            }
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "shouldSleep", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onShouldSleep(final CallbackInfoReturnable<Boolean> cir) {
        if (GuardCombatAndDutyHelper.shouldPreventGuardSleeping(this.worker)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "wakeUpGuard", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onWakeUpGuard(final CallbackInfoReturnable<IAIState> cir) {
        if (this.sleepingGuard == null) {
            return;
        }
        final EntityCitizen sleepingCitizen = this.sleepingGuard.get();
        if (sleepingCitizen == null
                || !(sleepingCitizen.getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard<?> guardJob)
                || !guardJob.isAsleep()) {
            return;
        }

        if (BlockPosUtil.getDistanceSquared(sleepingCitizen.blockPosition(), this.worker.blockPosition()) <= 2.25D) {
            if (GuardCombatAndDutyHelper.tryHarmlessWakeUpGuard(this.worker, sleepingCitizen)) {
                cir.setReturnValue(CombatAIStates.NO_TARGET);
            }
        }
    }

    @Inject(method = "shouldFlee", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onShouldFlee(final CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())
                && GuardCombatAndDutyHelper.shouldGuardRetreatOnLowHealth(this.worker, this.buildingGuards)) {
            cir.setReturnValue(true);
        }
    }
}
