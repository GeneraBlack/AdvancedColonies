package com.advancedcolonies.mixin.common.eating;

import com.advancedcolonies.common.eating.CitizenEatingHelper;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenFoodHandler;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenFoodHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link CitizenFoodHandler} that reports maximum food quality and diversity
 * stats ({@link CitizenEatingHelper#MAX_FOOD_HAPPINESS_STATS}) when {@code ignoreFoodLevelRequirements}
 * is enabled, preventing citizens from rejecting food or entering problem/complaint states after
 * consuming lower-level or vanilla food.
 */
@Mixin(value = CitizenFoodHandler.class, remap = false)
public abstract class CitizenFoodHandlerMixin {

    @Inject(method = "getFoodHappinessStats", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onGetFoodHappinessStats(
            final CallbackInfoReturnable<ICitizenFoodHandler.CitizenFoodStats> cir) {
        if (CitizenEatingHelper.shouldIgnoreFoodLevelRequirements()) {
            cir.setReturnValue(CitizenEatingHelper.MAX_FOOD_HAPPINESS_STATS);
        }
    }
}
