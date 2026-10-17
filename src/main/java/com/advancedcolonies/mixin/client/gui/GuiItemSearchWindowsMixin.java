package com.advancedcolonies.mixin.client.gui;

import com.advancedcolonies.client.gui.GuiSearchFilterHelper;
import com.minecolonies.core.client.gui.WindowHutAllInventory;
import com.minecolonies.core.client.gui.WindowPostBoxMain;
import com.minecolonies.core.client.gui.modules.building.ItemListModuleWindow;
import com.minecolonies.core.client.gui.modules.building.RestaurantMenuModuleWindow;
import com.ldtteam.structurize.client.gui.WindowSelectRes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Thin client-side Mixin redirecting {@link String#contains(CharSequence)} in MineColonies
 * and Structurize resource/item filter windows to {@link GuiSearchFilterHelper#matches}.
 */
@Mixin(
        value = {
                ItemListModuleWindow.class,
                RestaurantMenuModuleWindow.class,
                WindowHutAllInventory.class,
                WindowPostBoxMain.class,
                WindowSelectRes.class
        },
        remap = false
)
public abstract class GuiItemSearchWindowsMixin {

    @Redirect(
            method = {
                    "lambda$updateResources$1",
                    "lambda$updateResources$2",
                    "lambda$updateResources$4",
                    "updateResources"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"
            ),
            require = 0
    )
    private boolean advancedColonies$onFilterStringContains(final String candidate, final CharSequence query) {
        return GuiSearchFilterHelper.matches(candidate, query);
    }
}
