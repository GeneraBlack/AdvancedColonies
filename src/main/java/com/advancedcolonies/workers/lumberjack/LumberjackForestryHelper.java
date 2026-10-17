package com.advancedcolonies.workers.lumberjack;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingLumberjack;
import com.minecolonies.core.colony.jobs.JobLumberjack;
import com.minecolonies.core.entity.ai.workers.util.Tree;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherrackBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.minecolonies.api.items.ModTags.fungi;

/**
 * Domain helper for Lumberjack / Forester AI enhancements:
 * <ul>
 *   <li>Teleports unreachable canopy drops (saplings, sticks, apples, logs) down to the Lumberjack.</li>
 *   <li>Supports replanting tree stumps even when the Lumberjack has no saplings in inventory.</li>
 * </ul>
 */
public final class LumberjackForestryHelper {

    /** Horizontal radius around a felled tree scanned for elevated canopy drops. */
    private static final double CANOPY_HORIZONTAL_RADIUS = 7.0D;

    /** Vertical height above the tree base scanned for elevated canopy drops. */
    private static final double CANOPY_VERTICAL_HEIGHT = 28.0D;

    private LumberjackForestryHelper() {
    }

    /**
     * Teleports unreachable or elevated tree drops within the canopy column of the felled tree
     * directly to the Lumberjack's position so they are collected without breaking every leaf block.
     *
     * @param world  the world
     * @param worker the Lumberjack citizen entity
     * @param job    the Lumberjack job
     */
    public static void teleportCanopyDropsToLumberjack(
            @Nullable final Level world,
            @Nullable final AbstractEntityCitizen worker,
            @Nullable final JobLumberjack job) {

        if (!AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.teleportUnreachableTreeDrops.get()
                || world == null
                || worker == null) {
            return;
        }

        final BlockPos centerPos = (job != null && job.getTree() != null && job.getTree().getLocation() != null)
                ? job.getTree().getLocation()
                : worker.blockPosition();

        final AABB canopyBox = new AABB(centerPos)
                .inflate(CANOPY_HORIZONTAL_RADIUS, 2.0D, CANOPY_HORIZONTAL_RADIUS)
                .expandTowards(0.0D, CANOPY_VERTICAL_HEIGHT, 0.0D);

        final List<ItemEntity> drops = world.getEntitiesOfClass(
                ItemEntity.class,
                canopyBox,
                item -> item != null
                        && item.isAlive()
                        && (!item.getPersistentData().contains("PreventRemoteMovement")
                            || !item.getPersistentData().getBoolean("PreventRemoteMovement")));

        for (final ItemEntity drop : drops) {
            final BlockPos dropBlockPos = drop.blockPosition();
            final boolean isElevated = drop.getY() > worker.getY() + 1.25D;
            final boolean isInLeaves = world.getBlockState(dropBlockPos).is(BlockTags.LEAVES)
                    || world.getBlockState(dropBlockPos.below()).is(BlockTags.LEAVES);

            if (isElevated || isInLeaves) {
                drop.setPos(worker.getX(), worker.getY() + 0.25D, worker.getZ());
                drop.setDeltaMovement(0.0D, 0.0D, 0.0D);
                drop.setNoPickUpDelay();
            }
        }
    }

    /**
     * Attempts to replant a felled tree's stumps without requiring sapling items in the Lumberjack's
     * inventory when {@code lumberjackReplantWithoutSaplings} is enabled.
     *
     * @param world    the world
     * @param worker   the Lumberjack citizen entity
     * @param building the Lumberjack Hut building
     * @param job      the Lumberjack job
     * @param location the primary tree location
     * @return {@code true} if free replanting was handled and completed
     */
    public static boolean tryReplantWithoutSaplings(
            @Nullable final Level world,
            @Nullable final AbstractEntityCitizen worker,
            @Nullable final BuildingLumberjack building,
            @Nullable final JobLumberjack job,
            @NotNull final BlockPos location) {

        if (!AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.lumberjackReplantWithoutSaplings.get()
                || world == null
                || worker == null
                || building == null
                || job == null) {
            return false;
        }

        final Tree tree = job.getTree();
        if (tree == null) {
            return false;
        }

        final ItemStack saplingStack = tree.getSapling();
        if (ItemStackUtils.isEmpty(saplingStack) || !(saplingStack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        final Block saplingBlock = blockItem.getBlock();
        while (!tree.getStumpLocations().isEmpty()) {
            final BlockPos pos = tree.getStumpLocations().get(0);
            if (saplingStack.is(Tags.Items.MUSHROOMS)) {
                building.addNetherTree(pos);
            } else if (saplingStack.is(fungi)) {
                if (world.getBlockState(pos.below()).getBlock() instanceof NetherrackBlock) {
                    final Block nylium = saplingStack.is(Items.WARPED_FUNGUS)
                            ? Blocks.WARPED_NYLIUM
                            : Blocks.CRIMSON_NYLIUM;
                    world.setBlockAndUpdate(pos.below(), nylium.defaultBlockState());
                    building.addNetherTree(pos);
                }
            }

            final BlockState defaultSaplingState = saplingBlock.defaultBlockState();
            if (defaultSaplingState.canSurvive(world, pos)
                    && !Objects.equals(world.getBlockState(pos), defaultSaplingState)) {
                world.setBlockAndUpdate(pos, defaultSaplingState);
            }
            tree.removeStump(pos);
        }

        final SoundType soundType = saplingBlock.getSoundType(world.getBlockState(location), world, location, worker);
        world.playSound(
                null,
                worker.blockPosition(),
                soundType.getPlaceSound(),
                SoundSource.BLOCKS,
                soundType.getVolume(),
                soundType.getPitch());
        worker.swing(worker.getUsedItemHand());
        return true;
    }
}
