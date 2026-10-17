package com.advancedcolonies.workers.miner;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.BuilderAndExcavationConfig.WorkMode;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain helper for Miner and Quarrier excavation enhancements:
 * <ul>
 *   <li>Scales mined block drops by the Miner Hut's building level when {@code enableMinerDropMultiplier} is active.</li>
 *   <li>Determines when shaft/node walk steps should be bypassed in remote {@link WorkMode}s.</li>
 * </ul>
 */
public final class MinerExcavationHelper {

    private MinerExcavationHelper() {
    }

    /**
     * Checks whether the Miner should bypass physical ladder/shaft walking because a remote
     * {@link WorkMode} ({@link WorkMode#GOD} or {@link WorkMode#FORMALIST}) is active.
     *
     * @return {@code true} if shaft walking should be skipped
     */
    public static boolean shouldBypassShaftNavigation() {
        final WorkMode mode = AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.minerWorkMode.get();
        return mode == WorkMode.GOD || mode == WorkMode.FORMALIST;
    }

    /**
     * Scales mined block drops for Miners and Quarriers according to the building level
     * when {@code enableMinerDropMultiplier} is enabled.
     *
     * @param building the Miner Hut building
     * @param drops    the original block drops
     * @return the scaled list of item stacks
     */
    @NotNull
    public static List<ItemStack> scaleMinerBlockDrops(
            @Nullable final AbstractBuilding building,
            @NotNull final List<ItemStack> drops) {

        if (!AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.enableMinerDropMultiplier.get()
                || building == null
                || drops.isEmpty()) {
            return drops;
        }

        final int multiplier = Math.max(1, building.getBuildingLevel());
        if (multiplier <= 1) {
            return drops;
        }

        final List<ItemStack> scaledDrops = new ArrayList<>(drops.size() * multiplier);
        for (final ItemStack stack : drops) {
            if (stack.isEmpty()) {
                continue;
            }
            int remaining = stack.getCount() * multiplier;
            final int maxStack = Math.max(1, stack.getMaxStackSize());
            while (remaining > 0) {
                final int batch = Math.min(remaining, maxStack);
                final ItemStack copy = stack.copy();
                copy.setCount(batch);
                scaledDrops.add(copy);
                remaining -= batch;
            }
        }
        return scaledDrops;
    }
}
