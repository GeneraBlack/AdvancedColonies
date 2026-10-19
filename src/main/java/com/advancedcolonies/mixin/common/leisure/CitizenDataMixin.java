package com.advancedcolonies.mixin.common.leisure;

import com.advancedcolonies.common.eating.CitizenEatingHelper;
import com.advancedcolonies.common.leisure.CitizenLeisureHelper;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.core.colony.CitizenData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link CitizenData} to apply configurable leisure duration/cooldowns
 * and fix the {@code needsBetterFood()} copy-paste bug.
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

    /**
     * Fixes the vanilla copy-paste bug in {@code CitizenData#needsBetterFood()} where {@code slotBadFood}
     * and {@code slotGoodFood} tested the identical condition and always returned {@code false}.
     */
    @Inject(
            method = "needsBetterFood",
            at = @At("HEAD"),
            cancellable = true
    )
    private void advancedcolonies$fixNeedsBetterFood(final CallbackInfoReturnable<Boolean> cir) {
        if (CitizenEatingHelper.shouldIgnoreFoodLevelRequirements()
                || AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedEatingAI.get()) {
            final CitizenData self = (CitizenData) (Object) this;
            cir.setReturnValue(CitizenEatingHelper.evaluateNeedsBetterFood(self));
        }
    }
}
