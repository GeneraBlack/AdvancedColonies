package com.advancedcolonies.common.navigation;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.PathfindingConfig;
import com.ldtteam.domumornamentum.block.decorative.ShingleBlock;
import com.ldtteam.domumornamentum.block.decorative.ShingleSlabBlock;
import com.ldtteam.domumornamentum.block.decorative.TimberFrameBlock;
import com.ldtteam.domumornamentum.block.vanilla.SlabBlock;
import com.ldtteam.domumornamentum.block.vanilla.StairBlock;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.core.entity.pathfinding.MNode;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Evaluates and adjusts A* step traversal costs for citizen pathfinding.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Rewards walking on built roads, stairs, slabs (including Domum Ornamentum), and walkable building interiors.</li>
 *   <li>Rewards using ladders, vines, climbables, and minecart rails—especially during vertical transitions.</li>
 *   <li>Penalizes entering water, diving, walking on roofs/leaves, trampling farmland, and crossing berry bushes.</li>
 * </ul>
 */
public final class NavigationCostEvaluator {

    private NavigationCostEvaluator() {
        // Utility class
    }

    /**
     * Adjusts the base step cost computed by {@code AbstractPathJob.computeCost}.
     *
     * @param baseCost   the vanilla step cost computed by MineColonies.
     * @param world      the world level.
     * @param parent     the parent path node.
     * @param dY         vertical step delta ({@code y - parent.y}).
     * @param isSwimming whether the target node requires swimming.
     * @param onPath     whether MineColonies already classified the node as a path block.
     * @param isDiving   whether the target node is underwater diving.
     * @param onRails    whether the target node is on minecart rails.
     * @param swimStart  whether this step transitions from land into water.
     * @param ladder     whether the target node is a climbable ladder/vine node.
     * @param state      block state at the citizen's foot level {@code (x, y, z)}.
     * @param below      block state directly underneath {@code (x, y - 1, z)}.
     * @param x          target X coordinate.
     * @param y          target Y coordinate.
     * @param z          target Z coordinate.
     * @return the adjusted traversal cost (minimum {@code 0.05D}).
     */
    public static double adjustStepCost(
            final double baseCost,
            @NotNull final Level world,
            @NotNull final MNode parent,
            final int dY,
            final boolean isSwimming,
            final boolean onPath,
            final boolean isDiving,
            final boolean onRails,
            final boolean swimStart,
            final boolean ladder,
            @NotNull final BlockState state,
            @NotNull final BlockState below,
            final int x,
            final int y,
            final int z) {

        final PathfindingConfig cfg = AdvancedColoniesConfig.PATHFINDING;
        double cost = baseCost;

        // 1. Water & Diving Avoidance
        if (isSwimming) {
            if (swimStart) {
                cost += cfg.waterEnterCost.get();
            }
            if (isDiving) {
                cost += cfg.diveCost.get();
            }
            return Math.max(0.1D, cost);
        }

        // 2. Hazard & Obstacle Penalties (Roofs, Leaves, Farmland, Sweet Berry Bushes)
        final Block footBlock = state.getBlock();
        final Block belowBlock = below.getBlock();

        if (footBlock instanceof SweetBerryBushBlock || belowBlock instanceof SweetBerryBushBlock) {
            cost += cfg.berryBushPenalty.get();
        }

        if (belowBlock instanceof ShingleBlock
                || belowBlock instanceof ShingleSlabBlock
                || belowBlock instanceof LeavesBlock
                || below.is(BlockTags.LEAVES)) {
            cost += cfg.roofAndLeafPenalty.get();
        }

        if (dY != 0 && (belowBlock instanceof FarmBlock || footBlock instanceof FarmBlock)) {
            cost += cfg.farmlandJumpPenalty.get();
        }

        // Steeper drop penalty for drops >= 2 blocks so citizens prefer stairs/ladders over ledge drops
        if (dY < -1 && !ladder) {
            final int dropAbs = Math.abs(dY);
            cost += 0.25D * (dropAbs * dropAbs * dropAbs * dropAbs);
        }

        // 3. Climbable (Ladders & Vines) and Rail Preference
        if (cfg.preferClimbablesAndRails.get()) {
            if (ladder || isClimbableBlock(state) || isClimbableBlock(below)) {
                cost *= (dY != 0) ? 0.35D : 0.65D;
            }
            if (onRails || state.is(BlockTags.RAILS) || below.is(BlockTags.RAILS)
                    || footBlock instanceof BaseRailBlock || belowBlock instanceof BaseRailBlock) {
                cost *= 0.45D;
            }
        }

        // 4. Built Roads, Stairs, Slabs & Colony Building Footprints
        if (cfg.preferRoadsAndStructures.get()) {
            final boolean isRoadOrStair = onPath || isPlayerOrColonyRoadBlock(state, below);
            if (isRoadOrStair) {
                cost *= cfg.roadCostMultiplier.get();
                if (Math.abs(dY) == 1 && isStairOrSlab(state, below)) {
                    // Remove vertical step penalty when ascending/descending stairs or slabs
                    cost *= 0.6D;
                }
            }

            if (isInsideColonyBuilding(world, x, y, z)) {
                cost *= cfg.structurePathCostBonus.get();
            }
        }

        return Math.max(0.05D, cost);
    }

    /**
     * Checks whether the block at or below the node is a recognized road, path, stair, slab, or framed block.
     */
    public static boolean isPlayerOrColonyRoadBlock(@NotNull final BlockState state, @NotNull final BlockState below) {
        if (below.is(ModTags.pathingBlocks) || state.is(ModTags.pathingBlocks)) {
            return true;
        }
        if (below.getBlock() instanceof DirtPathBlock || state.getBlock() instanceof DirtPathBlock) {
            return true;
        }
        if (below.getBlock() instanceof TimberFrameBlock) {
            return true;
        }
        return isStairOrSlab(state, below);
    }

    /**
     * Checks whether either the foot block or the supporting floor block is a vanilla or Domum Ornamentum stair/slab.
     */
    public static boolean isStairOrSlab(@NotNull final BlockState state, @NotNull final BlockState below) {
        return below.is(BlockTags.STAIRS)
                || below.is(BlockTags.SLABS)
                || state.is(BlockTags.STAIRS)
                || state.is(BlockTags.SLABS)
                || below.getBlock() instanceof StairBlock
                || below.getBlock() instanceof SlabBlock
                || state.getBlock() instanceof StairBlock
                || state.getBlock() instanceof SlabBlock;
    }

    /**
     * Checks whether a block state is a ladder, vine, or other climbable block.
     */
    public static boolean isClimbableBlock(@NotNull final BlockState state) {
        final Block block = state.getBlock();
        return block instanceof LadderBlock
                || block instanceof VineBlock
                || state.is(BlockTags.CLIMBABLE);
    }

    /**
     * Checks whether the coordinate lies inside the bounding box of a constructed colony building.
     */
    private static boolean isInsideColonyBuilding(@NotNull final Level world, final int x, final int y, final int z) {
        final BlockPos pos = new BlockPos(x, y, z);
        @Nullable final IColony colony = IColonyManager.getInstance().getIColony(world, pos);
        if (colony == null || colony.getServerBuildingManager() == null) {
            return false;
        }
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
            if (building.getBuildingLevel() > 0 && building.isInBuilding(pos)) {
                return true;
            }
        }
        return false;
    }
}
