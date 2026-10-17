package com.advancedcolonies.mixin.buildings.restaurant;

import com.advancedcolonies.buildings.restaurant.RestaurantSeatSelector;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link BuildingCook} delegating free-seat lookup to {@link RestaurantSeatSelector}.
 */
@Mixin(value = BuildingCook.class, remap = false)
public abstract class BuildingCookMixin {

    @Inject(method = "getNextSittingPosition", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onGetNextSittingPosition(final CallbackInfoReturnable<BlockPos> cir) {
        if (AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedEatingAI.get()) {
            cir.setReturnValue(RestaurantSeatSelector.findAvailableSittingPosition((BuildingCook) (Object) this));
        }
    }
}
