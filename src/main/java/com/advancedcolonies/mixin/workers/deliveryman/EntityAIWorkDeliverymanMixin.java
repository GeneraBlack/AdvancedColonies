package com.advancedcolonies.mixin.workers.deliveryman;

import com.advancedcolonies.workers.deliveryman.DeliverymanLogisticsOptimizer;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingDeliveryman;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIWorkDeliveryman} delegating parallel delivery
 * batching and consecutive hut pickup chaining to {@link DeliverymanLogisticsOptimizer}.
 */
@Mixin(value = EntityAIWorkDeliveryman.class, remap = false)
public abstract class EntityAIWorkDeliverymanMixin
        extends AbstractEntityAIInteract<JobDeliveryman, BuildingDeliveryman> {

    protected EntityAIWorkDeliverymanMixin(@NotNull final JobDeliveryman job) {
        super(job);
    }

    @Shadow(remap = false)
    protected abstract boolean cannotHoldMoreItems();

    @Redirect(
            method = "prepareDelivery",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/entity/ai/workers/service/EntityAIWorkDeliveryman;getSecondarySkillLevel()I"
            ),
            remap = false,
            require = 0
    )
    private int advancedcolonies$expandParallelDeliveryLimit(final EntityAIWorkDeliveryman self) {
        return DeliverymanLogisticsOptimizer.getEffectiveParallelDeliverySkillLevel(this.getSecondarySkillLevel());
    }

    @Inject(
            method = "pickup",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/colony/jobs/JobDeliveryman;finishRequest(Z)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            cancellable = true,
            remap = false
    )
    private void advancedcolonies$chainConsecutivePickups(final CallbackInfoReturnable<IAIState> cir) {
        final IAIState chainedState = DeliverymanLogisticsOptimizer.tryChainNextPickup(
                this.job,
                this.worker,
                this.cannotHoldMoreItems());
        if (chainedState != null) {
            cir.setReturnValue(chainedState);
        }
    }
}
