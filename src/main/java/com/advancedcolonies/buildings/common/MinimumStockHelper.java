package com.advancedcolonies.buildings.common;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Helper for precise item-count Minimum Stock calculation and universal
 * Minimum Stock module attachment across colony huts.
 */
public final class MinimumStockHelper {

    /**
     * Buildings that do not support a standard Minimum Stock module
     * (either because they have no container storage or already use a specialized stock module).
     */
    private static final Set<String> EXCLUDED_BUILDING_IDS = Set.of(
            ModBuildings.POSTBOX_ID,
            ModBuildings.STASH_ID,
            ModBuildings.MYSTICAL_SITE_ID
    );

    private MinimumStockHelper() {
    }

    /**
     * Returns the multiplier applied to each Minimum Stock entry quantity.
     *
     * <p>In vanilla MineColonies, Minimum Stock quantities are multiplied by
     * {@link ItemStack#getMaxStackSize()} (typically 64). When
     * {@code enablePreciseMinimumStock} is active, each unit represents 1 item.</p>
     *
     * @param stack the stocked item stack
     * @return {@code 1} when precise minimum stock is enabled, or {@code stack.getMaxStackSize()} otherwise
     */
    public static int getEffectiveStackMultiplier(final ItemStack stack) {
        if (AdvancedColoniesConfig.LOGISTICS.enablePreciseMinimumStock.get()) {
            return 1;
        }
        return stack.getMaxStackSize();
    }

    /**
     * Attaches the {@link BuildingModules#MIN_STOCK} module producer to building entries
     * that do not already include it in vanilla MineColonies, and attaches a food menu
     * module to the Warehouse for courier dining.
     *
     * @param registryName the building's registry name
     * @param producers    the mutable list of module producers on the building entry builder
     */
    @SuppressWarnings("rawtypes")
    public static void attachUniversalModules(
            final ResourceLocation registryName,
            final List<BuildingEntry.ModuleProducer> producers) {
        if (registryName == null || producers == null) {
            return;
        }

        final String path = registryName.getPath();
        if (EXCLUDED_BUILDING_IDS.contains(path)) {
            return;
        }

        boolean hasMinStock = false;
        for (final BuildingEntry.ModuleProducer producer : producers) {
            if (producer == BuildingModules.MIN_STOCK || producer == BuildingModules.MIN_STOCK_POSTBOX) {
                hasMinStock = true;
                break;
            }
        }

        if (!hasMinStock) {
            producers.add(BuildingModules.MIN_STOCK);
        }

        // Attach a food menu module to the Warehouse so couriers can eat configured food there
        if (ModBuildings.WAREHOUSE_ID.equals(path) && !producers.contains(BuildingModules.NETHERMINER_MENU)) {
            producers.add(BuildingModules.NETHERMINER_MENU);
        }
    }
}
