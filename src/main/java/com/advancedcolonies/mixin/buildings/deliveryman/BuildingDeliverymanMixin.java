package com.advancedcolonies.mixin.buildings.deliveryman;

import com.advancedcolonies.buildings.common.BuildingCapacityHelper;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingDeliveryman;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link BuildingDeliveryman} ensuring that all assigned Couriers
 * in a multi-worker Deliveryman Hut are checked before allowing a food item to be eaten.
 */
@Mixin(value = BuildingDeliveryman.class, remap = false)
public abstract class BuildingDeliverymanMixin {

    @Inject(method = "canEat", at = @At("HEAD"), cancellable = true, remap = false)
    private void advancedcolonies$checkAllCouriersBeforeEating(
            final ItemStack stack,
            final CallbackInfoReturnable<Boolean> cir) {
        final BuildingDeliveryman self = (BuildingDeliveryman) (Object) this;
        if (BuildingCapacityHelper.isReservedByAnyCourier(self, stack)) {
            cir.setReturnValue(false);
        }
    }
}
