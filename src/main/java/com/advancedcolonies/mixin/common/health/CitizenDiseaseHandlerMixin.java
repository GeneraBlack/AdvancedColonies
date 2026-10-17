package com.advancedcolonies.mixin.common.health;

import com.advancedcolonies.common.health.CitizenHealthAndHospitalHelper;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenDiseaseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link CitizenDiseaseHandler} to support configurable doctor-seeking
 * health thresholds ({@code seekDoctorHealthRatio} and {@code seekDoctorAbsoluteHealth}).
 */
@Mixin(value = CitizenDiseaseHandler.class, remap = false)
public abstract class CitizenDiseaseHandlerMixin {

    @Shadow
    @Final
    private ICitizenData citizenData;

    /**
     * Evaluates configurable hurt thresholds for Hospital visits.
     *
     * @param cir callback info returnable
     */
    @Inject(method = "isHurt", at = @At("RETURN"), cancellable = true)
    private void advancedcolonies$evaluateConfigurableIsHurt(final CallbackInfoReturnable<Boolean> cir) {
        final boolean result = CitizenHealthAndHospitalHelper.evaluateIsHurt(this.citizenData, cir.getReturnValue());
        cir.setReturnValue(result);
    }
}
