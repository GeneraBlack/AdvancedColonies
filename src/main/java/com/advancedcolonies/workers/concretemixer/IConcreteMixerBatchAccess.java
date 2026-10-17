package com.advancedcolonies.workers.concretemixer;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * Duck interface implemented on {@code BuildingConcreteMixer} via Mixin to persist
 * the number of batched concrete powder blocks placed at each water position.
 */
public interface IConcreteMixerBatchAccess {

    /**
     * Records how many concrete powder blocks were combined into the placement at {@code pos}.
     *
     * @param pos   the water placement position
     * @param count the batch count placed
     */
    void advancedColonies$setBatchCount(@NotNull BlockPos pos, int count);

    /**
     * Returns the recorded batch count at {@code pos}, clamped to the active server limit.
     *
     * @param pos the water position being mined
     * @return the batch count (at least 1)
     */
    int advancedColonies$getBatchCount(@NotNull BlockPos pos);

    /**
     * Clears the recorded batch count at {@code pos} after mining completes.
     *
     * @param pos the mined water position
     */
    void advancedColonies$removeBatchCount(@NotNull BlockPos pos);
}
