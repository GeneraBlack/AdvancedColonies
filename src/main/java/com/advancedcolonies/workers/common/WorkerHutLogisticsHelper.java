package com.advancedcolonies.workers.common;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Optimizes how colony workers retrieve needed materials from their own hut.
 *
 * <p>When {@code pickMaterialsAtHutBlock} is enabled, workers walk directly to their
 * hut block instead of pathfinding to individual racks across the building, since
 * {@link AbstractTileEntityColonyBuilding} exposes a combined item handler over all
 * racks in the building.</p>
 */
public final class WorkerHutLogisticsHelper {

    private WorkerHutLogisticsHelper() {
    }

    /**
     * Resolves the target block position a worker should walk to when picking up
     * an item matching {@code itemPredicate} from their building.
     *
     * @param buildingTileEntity the building's tile entity
     * @param itemPredicate      the required item predicate
     * @return the hut block position (if direct hut pickup is enabled and the item exists),
     *         the specific container position, or {@code null} if not found
     */
    @Nullable
    public static BlockPos resolvePickupPosition(
            @Nullable final AbstractTileEntityColonyBuilding buildingTileEntity,
            final Predicate<ItemStack> itemPredicate) {
        if (buildingTileEntity == null) {
            return null;
        }

        final BlockPos chestPos = buildingTileEntity.getPositionOfChestWithItemStack(itemPredicate);
        if (chestPos == null) {
            return null;
        }

        if (AdvancedColoniesConfig.LOGISTICS.pickMaterialsAtHutBlock.get()) {
            return buildingTileEntity.getPosition();
        }

        return chestPos;
    }

    /**
     * Checks whether a worker's building has no remaining items matching {@code needsCurrently}
     * in any of its containers after a partial transfer (e.g. when the cook requests 64 food items
     * and the building racks only had 15, all 15 are transferred on the first attempt).
     *
     * @param building       the worker's building
     * @param needsCurrently the requested item predicate and count tuple
     * @return {@code true} if the building has no more matching items to transfer
     */
    public static boolean isBuildingDepletedOfItem(
            @Nullable final AbstractBuilding building,
            @Nullable final Tuple<Predicate<ItemStack>, Integer> needsCurrently) {
        if (building == null || needsCurrently == null || needsCurrently.getA() == null) {
            return false;
        }
        final AbstractTileEntityColonyBuilding tileEntity = building.getTileEntity();
        if (tileEntity == null) {
            return false;
        }
        return tileEntity.getPositionOfChestWithItemStack(needsCurrently.getA()) == null;
    }
}
