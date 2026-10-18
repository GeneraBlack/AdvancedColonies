package com.advancedcolonies.common.navigation;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.PathfindingConfig;
import com.ldtteam.domumornamentum.block.decorative.PanelBlock;
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
import com.minecolonies.core.entity.pathfinding.PathingOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates and adjusts A* step traversal costs for citizen pathfinding.
 * <p>
 * Key advantages over both Original MineColonies and ColonyPathingEdition:
 * <ul>
 *   <li><b>Main-Thread Snapshot + Lock-Free Off-Thread Lookup</b>: Snapshots constructed building
 *       bounding boxes into an immutable primitive {@code int[]} array on the main server thread
 *       when a path job is created (at most once every 5 seconds per dimension), so async pathfinding
 *       worker threads never touch {@link Level} or live colony maps off-thread.</li>
 *   <li><b>Deterministic Road Walking</b>: Suppresses vanilla's shared {@code Random} cost jitter
 *       ({@code randomnessFactor = 0}) to prevent atomic CAS contention across pathfinding threads
 *       and eliminate zig-zag paths on straight roads.</li>
 *   <li><b>Accurate Stair, Slab & Ladder Step Refunds</b>: Refunds the jump penalty that vanilla
 *       erroneously applies to Domum Ornamentum stairs, slabs, and ladder entry/exit steps.</li>
 *   <li><b>Unpowered Rail Protection</b>: Penalizes unpowered {@link PoweredRailBlock}s so minecarts
 *       never route onto brake rails.</li>
 * </ul>
 */
public final class NavigationCostEvaluator {

    /** Refresh interval (in milliseconds) for the thread-safe building bounding-box cache. */
    private static final long FOOTPRINT_CACHE_TTL_MS = 5000L;

    /** Thread-safe cache of flat primitive building bounding boxes (6 ints per building) per dimension. */
    private static final Map<ResourceKey<Level>, CachedDimensionFootprints> FOOTPRINT_CACHE = new ConcurrentHashMap<>();

    private record CachedDimensionFootprints(long timestampMs, int[] flatBoxes) {
    }

    private NavigationCostEvaluator() {
        // Utility class
    }

    /**
     * Called on the main server thread during {@code AbstractPathJob} construction to refresh
     * the building bounding-box snapshot if expired, ensuring async worker threads only read
     * an immutable {@code int[]} array.
     *
     * @param actualWorld the server world
     */
    public static void prepareFootprintsOnMainThread(@Nullable final Level actualWorld) {
        if (actualWorld == null || !AdvancedColoniesConfig.PATHFINDING.preferRoadsAndStructures.get()) {
            return;
        }
        final ResourceKey<Level> dim = actualWorld.dimension();
        final long now = System.currentTimeMillis();
        final CachedDimensionFootprints existing = FOOTPRINT_CACHE.get(dim);
        if (existing == null || (now - existing.timestampMs()) > FOOTPRINT_CACHE_TTL_MS) {
            FOOTPRINT_CACHE.put(dim, rebuildDimensionFootprints(actualWorld, now));
        }
    }

    /**
     * Optimizes {@link PathingOptions} at the start of a path job to eliminate shared-Random
     * CAS contention and random zig-zagging on straight roads.
     *
     * @param options the pathing options of the job
     */
    public static void optimizePathingOptions(@NotNull final PathingOptions options) {
        if (AdvancedColoniesConfig.PATHFINDING.preferRoadsAndStructures.get()) {
            options.randomnessFactor = 0.0D;
        }
    }

    /**
     * Adjusts the base step cost computed by {@code AbstractPathJob.computeCost}.
     *
     * @param baseCost       the vanilla step cost computed by MineColonies
     * @param pathingOptions the active pathing options
     * @param actualWorld    the level reference (used only for its {@link Level#dimension()} key off-thread)
     * @param parent         the parent path node
     * @param dY             vertical step delta ({@code y - parent.y})
     * @param isSwimming     whether the target node requires swimming
     * @param onPath         whether MineColonies already classified the node as a path block
     * @param isDiving       whether the target node is underwater diving
     * @param onRails        whether the target node is on minecart rails
     * @param swimStart      whether this step transitions from land into water
     * @param ladder         whether the target node is a climbable ladder/vine node
     * @param state          block state at the citizen's foot level {@code (x, y, z)}
     * @param below          block state directly underneath {@code (x, y - 1, z)}
     * @param x              target X coordinate
     * @param y              target Y coordinate
     * @param z              target Z coordinate
     * @return the adjusted traversal cost (minimum {@code 0.05D})
     */
    public static double adjustStepCost(
            final double baseCost,
            @NotNull final PathingOptions pathingOptions,
            @Nullable final Level actualWorld,
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

        final Block footBlock = state.getBlock();
        final Block belowBlock = below.getBlock();

        // 2. Unpowered Powered Rail Protection (prevents routing minecarts onto brake rails)
        if (onRails && footBlock instanceof PoweredRailBlock && !state.getValue(PoweredRailBlock.POWERED)) {
            return 50.0D;
        }

        // 3. Refund Vanilla's Erroneous Jump Penalty on 1-Block Ladder Entry/Exit and DO Stairs/Slabs
        if (!isDiving && Math.abs(dY) == 1) {
            final boolean isLadderTransition = (ladder != parent.isLadder())
                    && (ladder || parent.isLadder() || isClimbableBlock(state) || isClimbableBlock(below));
            final boolean isCustomStairOrSlabStep = !below.is(BlockTags.STAIRS)
                    && isStairOrSlab(state, below);
            if (isLadderTransition || isCustomStairOrSlabStep) {
                if (dY > 0) {
                    cost = Math.max(0.1D, cost - pathingOptions.jumpCost);
                } else if (pathingOptions.dropCost != 0) {
                    cost = Math.max(0.1D, cost - (pathingOptions.dropCost * 0.75D));
                }
            }
        }

        // 4. Hazard & Obstacle Penalties (Roofs, Leaves, Farmland, Sweet Berry Bushes, Cobwebs, Panels)
        if (footBlock instanceof SweetBerryBushBlock
                || belowBlock instanceof SweetBerryBushBlock
                || footBlock instanceof WebBlock) {
            cost += cfg.berryBushPenalty.get();
        }

        if (footBlock instanceof PanelBlock) {
            cost += 0.5D;
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

        // 5. Climbable (Ladders & Vines) and Rail Preference
        // Never apply surface cost discounts to multi-block free-falls (dY < -1 && !ladder).
        if (cfg.preferClimbablesAndRails.get()) {
            if (ladder || isClimbableBlock(state) || isClimbableBlock(below)) {
                cost *= (dY != 0) ? 0.35D : 0.65D;
            } else if (dY >= -1 && (onRails || state.is(BlockTags.RAILS) || below.is(BlockTags.RAILS)
                    || footBlock instanceof BaseRailBlock || belowBlock instanceof BaseRailBlock)) {
                cost *= 0.45D;
            }
        }

        // 6. Built Roads, Stairs, Slabs & Colony Building Footprints (only for normal steps dY >= -1)
        if (dY >= -1 && cfg.preferRoadsAndStructures.get()) {
            final boolean isRoadOrStair = onPath || isPlayerOrColonyRoadBlock(state, below);
            if (isRoadOrStair) {
                cost *= cfg.roadCostMultiplier.get();
                if (Math.abs(dY) == 1 && isStairOrSlab(state, below)) {
                    cost *= 0.6D;
                }
            }

            if (actualWorld != null && isInsideColonyBuildingFast(actualWorld.dimension(), x, y, z)) {
                cost *= cfg.structurePathCostBonus.get();
            }
        }

        // 7. Undiscounted Multi-Block Drop & Fall-Damage Avoidance Penalty
        // Applied AFTER all surface multipliers so landing on a road/building below never discounts a drop!
        // In Minecraft, falls > 3.0 blocks deal fall damage (and a 3-block drop from a stair/slab or onto a
        // dirt path/slab exceeds 3.0 blocks and deals fall damage).
        if (dY < -1 && !ladder) {
            final int dropAbs = Math.abs(dY);
            if (dropAbs == 2) {
                cost += 8.0D;
            } else if (dropAbs == 3) {
                cost += 80.0D;
            } else {
                final int excess = dropAbs - 2;
                cost += 1000.0D * excess * excess;
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
     * Zero-allocation, lock-free off-thread check whether {@code (x, y, z)} lies inside any
     * constructed colony building in {@code dimension}.
     */
    private static boolean isInsideColonyBuildingFast(
            @NotNull final ResourceKey<Level> dimension,
            final int x,
            final int y,
            final int z) {
        final CachedDimensionFootprints cached = FOOTPRINT_CACHE.get(dimension);
        if (cached == null) {
            return false;
        }
        final int[] boxes = cached.flatBoxes();
        for (int i = 0; i < boxes.length; i += 6) {
            if (x >= boxes[i] && x <= boxes[i + 3]
                    && z >= boxes[i + 2] && z <= boxes[i + 5]
                    && y >= boxes[i + 1] && y <= boxes[i + 4]) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    private static CachedDimensionFootprints rebuildDimensionFootprints(
            @NotNull final Level world,
            final long now) {
        try {
            final List<IColony> colonies = IColonyManager.getInstance().getColonies(world);
            if (colonies == null || colonies.isEmpty()) {
                return new CachedDimensionFootprints(now, new int[0]);
            }
            int totalBuildings = 0;
            for (final IColony colony : colonies) {
                if (colony.getServerBuildingManager() != null) {
                    totalBuildings += colony.getServerBuildingManager().getBuildings().size();
                }
            }
            final int[] flat = new int[totalBuildings * 6];
            int idx = 0;
            for (final IColony colony : colonies) {
                if (colony.getServerBuildingManager() == null) {
                    continue;
                }
                for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
                    if (building != null && building.getBuildingLevel() > 0 && idx + 6 <= flat.length) {
                        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
                        if (corners != null && corners.getA() != null && corners.getB() != null) {
                            final BlockPos a = corners.getA();
                            final BlockPos b = corners.getB();
                            flat[idx++] = Math.min(a.getX(), b.getX());
                            flat[idx++] = Math.min(a.getY(), b.getY());
                            flat[idx++] = Math.min(a.getZ(), b.getZ());
                            flat[idx++] = Math.max(a.getX(), b.getX());
                            flat[idx++] = Math.max(a.getY(), b.getY());
                            flat[idx++] = Math.max(a.getZ(), b.getZ());
                        }
                    }
                }
            }
            if (idx < flat.length) {
                final int[] trimmed = new int[idx];
                System.arraycopy(flat, 0, trimmed, 0, idx);
                return new CachedDimensionFootprints(now, trimmed);
            }
            return new CachedDimensionFootprints(now, flat);
        } catch (final Exception ignored) {
            return new CachedDimensionFootprints(now, new int[0]);
        }
    }
}
