package com.advancedcolonies.mixin.workers.deliveryman;

import com.advancedcolonies.workers.deliveryman.DeliverymanLogisticsOptimizer;
import com.advancedcolonies.workers.deliveryman.IJobDeliverymanAccess;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.IDeliverymanRequestable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedList;

/**
 * Thin Mixin hook on {@link JobDeliveryman} delegating multi-request batching
 * to {@link DeliverymanLogisticsOptimizer}.
 */
@Mixin(value = JobDeliveryman.class, remap = false)
public abstract class JobDeliverymanMixin implements IJobDeliverymanAccess {

    @Shadow(remap = false)
    private LinkedList<IToken<?>> getTaskQueueFromDataStore() {
        return null;
    }

    @Shadow(remap = false)
    private boolean haveTasksSameSourceAndDest(final Delivery requestA, final Delivery requestB) {
        return false;
    }

    @Override
    public LinkedList<IToken<?>> advancedColonies$getMutableTaskQueue() {
        return this.getTaskQueueFromDataStore();
    }

    @Override
    public boolean advancedColonies$haveTasksSameSourceAndDest(final Delivery requestA, final Delivery requestB) {
        return this.haveTasksSameSourceAndDest(requestA, requestB);
    }

    @Inject(method = "getCurrentTask", at = @At("RETURN"), remap = false)
    private void advancedcolonies$batchAdditionalWarehouseRequests(
            final CallbackInfoReturnable<IRequest<IDeliverymanRequestable>> cir) {
        if (cir.getReturnValue() != null) {
            final JobDeliveryman self = (JobDeliveryman) (Object) this;
            DeliverymanLogisticsOptimizer.pullAdditionalBatchRequests(self);
        }
    }

    @Inject(method = "getMaxParallelDeliveries", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void advancedcolonies$expandMaxParallelDeliveries(final CallbackInfoReturnable<Integer> cir) {
        final int effectiveSkill = DeliverymanLogisticsOptimizer.getEffectiveParallelDeliverySkillLevel(0);
        if (effectiveSkill > 0) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 1 + (effectiveSkill / 5)));
        }
    }
}
