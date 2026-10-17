package com.advancedcolonies.buildings.builder;

import com.advancedcolonies.AdvancedColonies;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ISettingsModule;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the Builder Hut "Clearing Strategy" setting (Top-to-Bottom vs. Bottom-to-Top).
 * <p>
 * When "Bottom to Top" is selected, the builder first clears terrain and obstacles from
 * the lowest blueprint layer upwards before running a cleanup pass from top to bottom.
 */
public final class BuilderClearingSettingHelper {

    /** Translation key for the default top-to-bottom clearing strategy. */
    public static final String CLEAR_TOP_DOWN = "com.advancedcolonies.builder.setting.clear.topdown";

    /** Translation key for the bottom-to-top clearing strategy. */
    public static final String CLEAR_BOTTOM_UP = "com.advancedcolonies.builder.setting.clear.bottomup";

    /** Setting key registered into the Builder Hut's {@link ISettingsModule}. */
    public static final ISettingKey<StringSetting> CLEAR_MODE = new SettingKey<>(
            StringSetting.class,
            ResourceLocation.fromNamespaceAndPath(AdvancedColonies.MOD_ID, "clearmode")
    );

    private BuilderClearingSettingHelper() {
        // Utility class
    }

    /**
     * Attaches the {@link #CLEAR_MODE} setting to a Builder Hut's settings module right after
     * {@link BuildingBuilder#BUILDING_MODE} is registered.
     *
     * @param module     the settings module being initialized.
     * @param addedKey   the setting key that was just added to the module.
     */
    public static void onSettingRegistered(@NotNull final ISettingsModule module, @NotNull final ISettingKey<?> addedKey) {
        if (BuildingBuilder.BUILDING_MODE.equals(addedKey)) {
            module.with(CLEAR_MODE, new StringSetting(CLEAR_TOP_DOWN, CLEAR_BOTTOM_UP));
        }
    }

    /**
     * Checks whether a given Builder Hut should execute a bottom-up clearing pass first.
     *
     * @param building the Builder Hut building instance.
     * @return {@code true} if bottom-up clearing is enabled in config and selected in the hut settings.
     */
    public static boolean shouldClearFromBottomUp(@Nullable final IBuilding building) {
        if (building == null || !AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.enableBottomUpClearingOption.get()) {
            return false;
        }
        final String selected = building.getSettingValueOrDefault(CLEAR_MODE, CLEAR_TOP_DOWN);
        return CLEAR_BOTTOM_UP.equals(selected);
    }
}
