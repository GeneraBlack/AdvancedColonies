package com.advancedcolonies.mixin.workers.deliveryman;

import com.advancedcolonies.workers.deliveryman.DeliverymanLogisticsOptimizer;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.IDeliverymanRequestable;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link JobDeliveryman} delegating multi-request batching
 * to {@link DeliverymanLogisticsOptimizer}.
 */
@Mixin(value = JobDeliveryman.class, remap = false)
public abstract class JobDeliverymanMixin {

    @Inject(method = "getCurrentTask", at = @At("RETURN"), remap = false)
    private void advancedcolonies$batchAdditionalWarehouseRequests(
            final CallbackInfoReturnable<IRequest<IDeliverymanRequestable>> cir) {
        if (cir.getReturnValue() != null) {
            final JobDeliveryman self = (JobDeliveryman) (Object) this;
            DeliverymanLogisticsOptimizer.pullAdditionalBatchRequests(self);
        }
    }
}
