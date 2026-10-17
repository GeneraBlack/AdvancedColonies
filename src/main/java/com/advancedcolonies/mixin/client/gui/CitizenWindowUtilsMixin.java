package com.advancedcolonies.mixin.client.gui;

import com.advancedcolonies.client.gui.ClientGuiEnhancementHelper;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.gui.citizen.CitizenWindowUtils;
import com.minecolonies.core.client.gui.citizen.JobWindowCitizen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin client-side Mixin hook on {@link CitizenWindowUtils} to attach a quick-jump button
 * on the citizen Job window that opens the worker's building GUI directly.
 */
@Mixin(value = CitizenWindowUtils.class, remap = false)
public abstract class CitizenWindowUtilsMixin {

    /**
     * Attaches the workplace quick-jump button after the job page is populated.
     *
     * @param citizen       the citizen data view
     * @param windowCitizen the job window
     * @param colony        the colony view
     * @param ci            callback info
     */
    @Inject(method = "updateJobPage", at = @At("RETURN"))
    private static void advancedcolonies$attachWorkBuildingQuickJump(
            final ICitizenDataView citizen,
            final JobWindowCitizen windowCitizen,
            final IColonyView colony,
            final CallbackInfo ci) {
        ClientGuiEnhancementHelper.attachWorkBuildingQuickJumpButton(citizen, windowCitizen, colony);
    }
}
