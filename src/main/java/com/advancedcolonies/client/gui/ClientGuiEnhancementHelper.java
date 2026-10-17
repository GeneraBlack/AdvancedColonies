package com.advancedcolonies.client.gui;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.ldtteam.blockui.controls.ButtonImage;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.gui.citizen.JobWindowCitizen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side GUI helper for Minimum Stock exact item counts and quick Citizen-to-Building navigation.
 *
 * <p>Controlled via {@link AdvancedColoniesConfig#LOGISTICS}:
 * <ul>
 *   <li>{@code enablePreciseMinimumStock}</li>
 *   <li>{@code enableUiQuickNavigation}</li>
 * </ul>
 */
public final class ClientGuiEnhancementHelper {

    private static final String QUICK_JUMP_WORK_BUTTON_ID = "ac_open_work_hut";

    private ClientGuiEnhancementHelper() {
    }

    /**
     * Adjusts the display stack size for the Minimum Stock list row icon so that when
     * {@code enablePreciseMinimumStock} is enabled, the icon displays a single item
     * instead of a full stack.
     *
     * @param stack the item stack being rendered in the minimum stock row
     */
    public static void adjustMinimumStockDisplayStack(final ItemStack stack) {
        if (stack != null && AdvancedColoniesConfig.LOGISTICS.enablePreciseMinimumStock.get()) {
            stack.setCount(1);
        }
    }

    /**
     * Attaches a quick-jump button on the citizen Job window that opens the citizen's assigned
     * workplace building GUI directly.
     *
     * @param citizen       the citizen data view
     * @param windowCitizen the citizen job window
     * @param colony        the colony view
     */
    public static void attachWorkBuildingQuickJumpButton(
            final ICitizenDataView citizen,
            final JobWindowCitizen windowCitizen,
            final IColonyView colony) {
        if (citizen == null || windowCitizen == null || colony == null) {
            return;
        }
        if (!AdvancedColoniesConfig.LOGISTICS.enableUiQuickNavigation.get()) {
            return;
        }
        if (citizen.getWorkBuilding() == null) {
            return;
        }

        final IBuildingView workBuilding = colony.getClientBuildingManager().getBuilding(citizen.getWorkBuilding());
        if (workBuilding == null) {
            return;
        }

        if (windowCitizen.findPaneByID(QUICK_JUMP_WORK_BUTTON_ID) != null) {
            return;
        }

        final ButtonImage jumpBtn = new ButtonImage();
        jumpBtn.setID(QUICK_JUMP_WORK_BUTTON_ID);
        jumpBtn.setImage(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/builderhut/builder_button_mini.png"));
        jumpBtn.setSize(18, 18);
        jumpBtn.setPosition(168, 18);
        jumpBtn.setText(Component.literal("->"));
        jumpBtn.setHandler(btn -> workBuilding.openGui(false));
        windowCitizen.addChild(jumpBtn);
    }
}
