package com.advancedcolonies.workers.concretemixer;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.WorkerFurnaceAndCraftConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Domain helper for Concrete Mixer batch placement and harvesting, enforcing server limits:
 * <ul>
 *   <li>Computes batch size as {@code 1 + secondarySkillLevel / concreteMixerSkillDivisor}.</li>
 *   <li>Strictly clamps batch size to {@code concreteMixerMaxBatchSize} both when placing and when harvesting NBT-persisted blocks.</li>
 * </ul>
 */
public final class ConcreteMixerBatchHelper {

    private ConcreteMixerBatchHelper() {
    }

    /**
     * Checks whether Concrete Mixer batching is enabled in the server configuration.
     *
     * @return {@code true} if enabled
     */
    public static boolean isBatchingEnabled() {
        return AdvancedColoniesConfig.FURNACE_AND_CRAFT.enableConcreteMixerBatching.get();
    }

    /**
     * Returns the strict server maximum batch size for Concrete Mixer operations.
     *
     * @return maximum batch size (1..64)
     */
    public static int getServerMaxBatchSize() {
        return Math.max(1, Math.min(64, AdvancedColoniesConfig.FURNACE_AND_CRAFT.concreteMixerMaxBatchSize.get()));
    }

    /**
     * Computes the number of concrete powder blocks to place in a single pass, clamped to both
     * the available stack size and the server limit {@code concreteMixerMaxBatchSize}.
     *
     * @param availableCount      the stack size of concrete powder in the worker's inventory slot
     * @param secondarySkillLevel the worker's Secondary Skill level
     * @return the clamped batch quantity to place (at least 1)
     */
    public static int computeBatchPlacementQuantity(final int availableCount, final int secondarySkillLevel) {
        if (!isBatchingEnabled()) {
            return 1;
        }
        final WorkerFurnaceAndCraftConfig cfg = AdvancedColoniesConfig.FURNACE_AND_CRAFT;
        final int divisor = Math.max(1, cfg.concreteMixerSkillDivisor.get());
        final int skillBatch = 1 + Math.max(0, secondarySkillLevel) / divisor;
        final int clampedToServer = Math.min(skillBatch, getServerMaxBatchSize());
        return Math.max(1, Math.min(availableCount, clampedToServer));
    }

    /**
     * Retrieves the effective harvest multiplier for a mined concrete block at {@code pos},
     * strictly clamped to {@code concreteMixerMaxBatchSize}.
     *
     * @param building the Concrete Mixer building
     * @param pos      the mined block position
     * @return the clamped harvest multiplier (at least 1)
     */
    public static int getClampedHarvestMultiplier(@Nullable final Object building, @Nullable final BlockPos pos) {
        if (!isBatchingEnabled() || !(building instanceof IConcreteMixerBatchAccess access) || pos == null) {
            return 1;
        }
        final int stored = access.advancedColonies$getBatchCount(pos);
        return Math.max(1, Math.min(stored, getServerMaxBatchSize()));
    }

    /**
     * Multiplies the mined concrete block drops by the recorded batch multiplier (clamped to server limit).
     *
     * @param drops    the original block drops
     * @param building the Concrete Mixer building
     * @param pos      the mined block position
     * @return the scaled drops list
     */
    @NotNull
    public static List<ItemStack> scaleConcreteDrops(
            @NotNull final List<ItemStack> drops,
            @Nullable final Object building,
            @Nullable final BlockPos pos) {

        final int multiplier = getClampedHarvestMultiplier(building, pos);
        if (multiplier <= 1) {
            return drops;
        }

        for (final ItemStack stack : drops) {
            if (!stack.isEmpty()) {
                stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() * multiplier));
            }
        }
        return drops;
    }
}
