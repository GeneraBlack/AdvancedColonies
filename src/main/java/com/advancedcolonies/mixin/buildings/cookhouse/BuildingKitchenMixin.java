package com.advancedcolonies.mixin.buildings.cookhouse;

import com.advancedcolonies.buildings.common.BuildingCapacityHelper;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link BuildingKitchen} ensuring that all assigned Chefs
 * in a multi-worker Cookhouse are checked before allowing a food item to be eaten.
 */
@Mixin(value = BuildingKitchen.class, remap = false)
public abstract class BuildingKitchenMixin {

    @Inject(method = "canEat", at = @At("HEAD"), cancellable = true, remap = false)
    private void advancedcolonies$checkAllChefsBeforeEating(
            final ItemStack stack,
            final CallbackInfoReturnable<Boolean> cir) {
        final BuildingKitchen self = (BuildingKitchen) (Object) this;
        if (BuildingCapacityHelper.isReservedByAnyChef(self, stack)) {
            cir.setReturnValue(false);
        }
    }
}
