package com.advancedcolonies.workers.builder;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.core.entity.pathfinding.PathfindingUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stateless utility that evaluates whether a builder's standing position is safe
 * during mining and block placement, and searches for safe adjacent alternative stands.
 * <p>
 * Protects builders against three common construction hazards:
 * <ul>
 *   <li><b>Support-Block Drops:</b> Breaking the block directly underneath the builder when a deep shaft lies below.</li>
 *   <li><b>Overhead Falling Blocks:</b> Breaking a ceiling block at head/above-head level when sand or gravel rests on top.</li>
 *   <li><b>Adjacent Lava Hazards:</b> Standing next to or above open lava blocks/fluids.</li>
 * </ul>
 */
public final class BuilderStandSafetyEvaluator {

    private BuilderStandSafetyEvaluator() {
        // Utility class
    }

    /**
     * Checks whether mining {@code blockToMine} while standing at {@code standPos} is unsafe.
     *
     * @param world       the world level.
     * @param blockToMine the target block to be broken.
     * @param standPos    the builder's current or planned foot position.
     * @return {@code true} if breaking the block from {@code standPos} would endanger the builder.
     */
    public static boolean isUnsafeMiningStand(
            @NotNull final Level world,
            @NotNull final BlockPos blockToMine,
            @NotNull final BlockPos standPos) {

        if (!AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.enableSafeMiningStandSelection.get()) {
            return false;
        }

        // 1. Support block directly underneath the builder's feet
        if (blockToMine.equals(standPos.below()) && wouldCauseUnsafeFall(world, blockToMine)) {
            return true;
        }

        // 2. Overhead block with a gravity-affected block (sand, gravel, etc.) resting on top of it.
        // Checks both head height (Y+1) and directly above head (Y+2) for a 2-block-tall citizen.
        if (AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.protectAgainstOverheadFallingBlocks.get()) {
            if ((blockToMine.equals(standPos.above()) || blockToMine.equals(standPos.above(2)))
                    && isFallingBlock(world.getBlockState(blockToMine.above()))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Searches for a safe horizontal neighbor stand around {@code blockToMine} when {@code currentStand} is unsafe.
     *
     * @param world         the world level.
     * @param blockToMine   the target block to mine.
     * @param currentStand  the currently unsafe stand position.
     * @param hazardTracker the builder's lava hazard tracker (to avoid recently blocked lava zones).
     * @return a safe alternative {@link BlockPos} to stand on, or {@code null} if none is immediately available.
     */
    @Nullable
    public static BlockPos findAlternativeMiningStand(
            @NotNull final Level world,
            @NotNull final BlockPos blockToMine,
            @NotNull final BlockPos currentStand,
            @Nullable final BuilderLavaHazardTracker hazardTracker) {

        if (!isUnsafeMiningStand(world, blockToMine, currentStand)) {
            return null;
        }

        final BlockPos center = new BlockPos(blockToMine.getX(), currentStand.getY(), blockToMine.getZ());
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Check cardinal neighbors first, then diagonal/ring neighbors up to radius 3 across vertical offsets
        final int[] yOffsets = {0, -1, 1, -2, 2};
        for (final int dy : yOffsets) {
            final BlockPos baseCenter = center.offset(0, dy, 0);
            for (final Direction direction : Direction.Plane.HORIZONTAL) {
                final BlockPos candidate = baseCenter.relative(direction);
                if (!candidate.equals(currentStand)
                        && canUseStandPosition(world, candidate)
                        && !isUnsafeMiningStand(world, blockToMine, candidate)
                        && (hazardTracker == null || !hazardTracker.isBlockedByRecentHazard(blockToMine, candidate))
                        && findLavaNearStand(world, cursor, candidate) == null) {
                    return candidate;
                }
            }
        }

        for (int radius = 1; radius <= 3; radius++) {
            for (final int dy : yOffsets) {
                final BlockPos baseCenter = center.offset(0, dy, 0);
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        final BlockPos candidate = baseCenter.offset(dx, 0, dz);
                        if (!candidate.equals(currentStand)
                                && canUseStandPosition(world, candidate)
                                && !isUnsafeMiningStand(world, blockToMine, candidate)
                                && (hazardTracker == null || !hazardTracker.isBlockedByRecentHazard(blockToMine, candidate))
                                && findLavaNearStand(world, cursor, candidate) == null) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Checks whether breaking {@code supportBlock} would cause a vertical drop exceeding
     * {@link com.advancedcolonies.config.BuilderAndExcavationConfig#maxSafeSupportFallDepth}.
     *
     * @param world        the world level.
     * @param supportBlock the block directly below the builder.
     * @return {@code true} if the drop below {@code supportBlock} exceeds the configured safe fall depth.
     */
    public static boolean wouldCauseUnsafeFall(@NotNull final Level world, @NotNull final BlockPos supportBlock) {
        final int maxSafeFall = AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.maxSafeSupportFallDepth.get();
        int emptyBlocksBelow = 0;
        final BlockPos.MutableBlockPos cursor = supportBlock.mutable().move(Direction.DOWN);

        while (cursor.getY() >= world.getMinBuildHeight()) {
            final BlockState state = world.getBlockState(cursor);
            if (state.blocksMotion() || !state.getFluidState().isEmpty()) {
                // If the landing surface is lava, any drop into it is unsafe
                return PathfindingUtils.isLava(world, cursor, state, state.getFluidState());
            }

            emptyBlocksBelow++;
            if (emptyBlocksBelow > maxSafeFall) {
                return true;
            }
            cursor.move(Direction.DOWN);
        }

        return true;
    }

    /**
     * Validates that a stand position has solid footing and 2 blocks of unobstructed, fluid-free headroom.
     *
     * @param world    the world level.
     * @param standPos the candidate foot position.
     * @return {@code true} if a citizen can safely stand at {@code standPos}.
     */
    public static boolean canUseStandPosition(@NotNull final Level world, @NotNull final BlockPos standPos) {
        final BlockState floor = world.getBlockState(standPos.below());
        final BlockState feet = world.getBlockState(standPos);
        final BlockState head = world.getBlockState(standPos.above());
        return floor.blocksMotion()
                && !feet.blocksMotion()
                && feet.getFluidState().isEmpty()
                && !head.blocksMotion()
                && head.getFluidState().isEmpty();
    }

    /**
     * Scans a 3x3 column around {@code stand} (from Y-1 to Y+1) for any lava block or fluid.
     *
     * @param world  the world level.
     * @param cursor reusable mutable block position.
     * @param stand  the stand position to check.
     * @return the position of the detected lava hazard, or {@code null} if lava-free.
     */
    @Nullable
    public static BlockPos findLavaNearStand(
            @NotNull final Level world,
            @NotNull final BlockPos.MutableBlockPos cursor,
            @NotNull final BlockPos stand) {

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    cursor.set(stand.getX() + dx, stand.getY() + dy, stand.getZ() + dz);
                    if (PathfindingUtils.isLava(world, cursor, world.getBlockState(cursor), world.getFluidState(cursor))) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static boolean isFallingBlock(@NotNull final BlockState state) {
        return state.getBlock() instanceof FallingBlock;
    }
}
