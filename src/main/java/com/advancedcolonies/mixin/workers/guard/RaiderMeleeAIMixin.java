package com.advancedcolonies.mixin.workers.guard;

import com.advancedcolonies.workers.guard.GuardCombatAndDutyHelper;
import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import com.minecolonies.core.entity.ai.combat.AttackMoveAI;
import com.minecolonies.core.entity.mobs.aitasks.RaiderMeleeAI;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin on {@link RaiderMeleeAI} delegating Raider threat rebalancing and fleeing Guard
 * aggro reduction to {@link GuardCombatAndDutyHelper}.
 */
@Mixin(value = RaiderMeleeAI.class, remap = false)
public abstract class RaiderMeleeAIMixin<T extends AbstractEntityMinecoloniesMonster & IThreatTableEntity>
        extends AttackMoveAI<T> {

    protected RaiderMeleeAIMixin(final T owner, final ITickRateStateMachine<IState> stateMachine) {
        super(owner, stateMachine);
    }

    @Override
    protected boolean checkForTarget() {
        if (!GuardCombatAndDutyHelper.evaluateRaiderCurrentTarget(user, target)) {
            resetTarget();
            return false;
        }
        return super.checkForTarget();
    }

    @Override
    protected boolean searchNearbyTarget() {
        GuardCombatAndDutyHelper.applyRaiderNearbyGuardThreat(user, getSearchArea());
        return super.searchNearbyTarget();
    }

    @Inject(method = "isAttackableTarget", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onIsAttackableTarget(
            final LivingEntity candidate,
            final CallbackInfoReturnable<Boolean> cir) {
        if (GuardCombatAndDutyHelper.isRaiderThreatRebalancingEnabled()) {
            cir.setReturnValue(GuardCombatAndDutyHelper.isRaiderAttackableTarget(candidate));
        }
    }
}
