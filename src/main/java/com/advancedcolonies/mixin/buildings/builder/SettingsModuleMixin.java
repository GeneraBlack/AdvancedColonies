package com.advancedcolonies.mixin.buildings.builder;

import com.advancedcolonies.buildings.builder.BuilderClearingSettingHelper;
import com.minecolonies.api.colony.buildings.modules.ISettingsModule;
import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook that registers the Builder's "Clearing Strategy" setting
 * whenever a Builder Hut's {@link SettingsModule} registers its construction mode setting.
 */
@Mixin(value = SettingsModule.class, remap = false)
public abstract class SettingsModuleMixin implements ISettingsModule {

    @Inject(method = "with", at = @At("RETURN"))
    private void advancedColonies$onWithSetting(
            final ISettingKey<?> key,
            final ISetting<?> setting,
            final CallbackInfoReturnable<ISettingsModule> cir) {
        BuilderClearingSettingHelper.onSettingRegistered(this, key);
    }
}
