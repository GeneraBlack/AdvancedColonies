package com.advancedcolonies.mixin.common.eating;

import com.advancedcolonies.common.eating.CitizenEatingHelper;
import com.minecolonies.core.items.ItemFood;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link ItemFood} that elevates all MineColonies food items to the maximum food
 * tier ({@link CitizenEatingHelper#MAX_FOOD_TIER}) when {@code ignoreFoodLevelRequirements} is enabled.
 */
@Mixin(value = ItemFood.class, remap = false)
public abstract class ItemFoodMixin {

    @Inject(method = "getTier", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onGetTier(final CallbackInfoReturnable<Integer> cir) {
        if (CitizenEatingHelper.shouldIgnoreFoodLevelRequirements()) {
            cir.setReturnValue(CitizenEatingHelper.MAX_FOOD_TIER);
        }
    }
}
