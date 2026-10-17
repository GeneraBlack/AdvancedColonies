package com.advancedcolonies.mixin.common.leisure;

import com.advancedcolonies.common.leisure.CitizenLeisureHelper;
import com.minecolonies.core.colony.CitizenData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin Mixin hook on {@link CitizenData} to apply configurable leisure duration and cooldowns.
 */
@Mixin(value = CitizenData.class, remap = false)
public abstract class CitizenDataMixin {

    @Shadow
    private int leisureTime;

    /**
     * Captures the citizen's leisure time before vanilla's update logic runs and replaces it with
     * the configurable leisure calculation from {@link CitizenLeisureHelper}.
     *
     * @param tickRate the update tick rate
     * @param ci       callback info
     */
    @Inject(
            method = "update",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/minecolonies/core/colony/CitizenData;interactedRecently:I",
                    ordinal = 0
            )
    )
    private void advancedcolonies$applyConfigurableLeisureTime(final int tickRate, final CallbackInfo ci) {
        final CitizenData self = (CitizenData) (Object) this;
        this.leisureTime = CitizenLeisureHelper.computeUpdatedLeisureTime(self, this.leisureTime, tickRate);
    }
}
