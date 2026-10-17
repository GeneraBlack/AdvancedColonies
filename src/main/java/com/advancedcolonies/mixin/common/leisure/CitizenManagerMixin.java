package com.advancedcolonies.mixin.common.leisure;

import com.advancedcolonies.common.leisure.CitizenMournHelper;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.managers.CitizenManager;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin Mixin hook on {@link CitizenManager} delegating essential-worker mourning exemptions
 * and morning mourn-state sanitization to {@link CitizenMournHelper}.
 */
@Mixin(value = CitizenManager.class, remap = false)
public abstract class CitizenManagerMixin {

    @Shadow
    public abstract List<ICitizenData> getCitizens();

    @Inject(method = "updateCitizenMourn", at = @At("RETURN"))
    private void advancedColonies$onAfterUpdateCitizenMourn(
            final ICitizenData data,
            final boolean mourn,
            final CallbackInfo ci) {
        for (final ICitizenData citizen : this.getCitizens()) {
            CitizenMournHelper.sanitizeCitizenMournState(citizen);
        }
    }

    @Inject(method = "onWakeUp", at = @At("RETURN"))
    private void advancedColonies$onAfterWakeUp(final CallbackInfo ci) {
        for (final ICitizenData citizen : this.getCitizens()) {
            CitizenMournHelper.sanitizeCitizenMournState(citizen);
        }
    }
}
