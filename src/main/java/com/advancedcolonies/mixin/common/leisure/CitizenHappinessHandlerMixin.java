package com.advancedcolonies.mixin.common.leisure;

import com.advancedcolonies.workers.guard.GuardPatrolAndSwarmHelper;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link CitizenHappinessHandler#getGuardFactor(IColony)} that rewards
 * well-distributed Guard patrols across the colony with a spatial coverage bonus to Security happiness.
 */
@Mixin(value = CitizenHappinessHandler.class, remap = false)
public abstract class CitizenHappinessHandlerMixin {

    @Inject(method = "getGuardFactor", at = @At("RETURN"), cancellable = true)
    private static void advancedColonies$onGetGuardFactorReturn(
            final IColony colony,
            final CallbackInfoReturnable<Double> cir) {

        final double baseFactor = cir.getReturnValue() != null ? cir.getReturnValue() : 1.0D;
        cir.setReturnValue(GuardPatrolAndSwarmHelper.computeSecurityFactorWithCoverageBonus(colony, baseFactor));
    }
}
