package com.advancedcolonies.workers.farmer;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Domain helper for Farmer and Planter AI enhancements:
 * <ul>
 *   <li>Streamlines Farmer harvesting by immediately collecting dropped crops/seeds and replanting on tilled soil in a single pass.</li>
 *   <li>Accelerates Farmer per-tile action delay.</li>
 *   <li>Sweeps nearby harvested plantation items directly to the Planter so drops into water/cane beds are not lost.</li>
 * </ul>
 */
public final class AgricultureWorkHelper {

    /** Radius around a harvested plantation block swept for dropped items. */
    private static final double PLANTER_SWEEP_RADIUS = 4.0D;

    private AgricultureWorkHelper() {
    }

    /**
     * Adjusts the Farmer's per-tile action delay when {@code enableEnhancedFarmerAI} is enabled.
     *
     * @param vanillaDelay the vanilla delay in ticks
     * @return the reduced delay in ticks
     */
    public static int computeFarmerActionDelay(final int vanillaDelay) {
        if (!AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.enableEnhancedFarmerAI.get()) {
            return vanillaDelay;
        }
        return Math.max(1, vanillaDelay / 2);
    }

    /**
     * Immediately collects nearby crop drops and attempts to replant the harvested tile in the same pass
     * if the soil below remains valid farmland and the Farmer has matching seeds in their inventory.
     *
     * @param world       the world
     * @param worker      the Farmer citizen entity
     * @param farmField   the active farm field
     * @param surfacePos  the farmland surface position (whose {@code above()} was just harvested)
     */
    public static void onAfterFarmerHarvest(
            @Nullable final Level world,
            @Nullable final AbstractEntityCitizen worker,
            @Nullable final FarmField farmField,
            @Nullable final BlockPos surfacePos) {

        if (!AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.enableEnhancedFarmerAI.get()
                || world == null
                || worker == null
                || surfacePos == null) {
            return;
        }

        // Pull harvested drops within 3 blocks so newly dropped seeds can be used immediately
        sweepNearbyDropsToWorker(world, worker, surfacePos.above(), 3.0D);
        CitizenItemUtils.pickupItems(worker);

        if (farmField == null) {
            return;
        }

        final ItemStack seedStack = farmField.getSeed();
        if (seedStack == null || seedStack.isEmpty()) {
            return;
        }

        final Item seedItem = seedStack.getItem();
        if (seedItem == Items.MELON_SEEDS || seedItem == Items.PUMPKIN_SEEDS) {
            // Stem crops leave their stem behind when harvesting the melon/pumpkin fruit
            return;
        }

        final BlockPos cropPos = surfacePos.above();
        if (!world.isEmptyBlock(cropPos)) {
            return;
        }

        final int slot = worker.getCitizenInventoryHandler().findFirstSlotInInventoryWith(seedItem);
        if (slot == -1) {
            return;
        }

        if (seedItem instanceof BlockItem blockItem) {
            final Block cropBlock = blockItem.getBlock();
            if ((cropBlock instanceof CropBlock || cropBlock instanceof StemBlock || cropBlock instanceof MinecoloniesCropBlock)
                    && cropBlock.defaultBlockState().canSurvive(world, cropPos)) {
                world.setBlockAndUpdate(cropPos, cropBlock.defaultBlockState());
                worker.getInventoryCitizen().extractItem(slot, 1, false);
            }
        }
    }

    /**
     * Sweeps harvested plantation drops near the action position to the Planter when
     * {@code enableEnhancedPlanterAI} is enabled.
     *
     * @param world     the world
     * @param worker    the Planter citizen entity
     * @param actionPos the harvested block position
     */
    public static void onAfterPlanterHarvest(
            @Nullable final Level world,
            @Nullable final AbstractEntityCitizen worker,
            @Nullable final BlockPos actionPos) {

        if (!AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.enableEnhancedPlanterAI.get()
                || world == null
                || worker == null
                || actionPos == null) {
            return;
        }

        sweepNearbyDropsToWorker(world, worker, actionPos, PLANTER_SWEEP_RADIUS);
        CitizenItemUtils.pickupItems(worker);
    }

    /**
     * Teleports nearby item entities around {@code center} directly to the worker for immediate collection.
     */
    private static void sweepNearbyDropsToWorker(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen worker,
            @NotNull final BlockPos center,
            final double radius) {

        final AABB sweepBox = new AABB(center).inflate(radius);
        final List<ItemEntity> drops = world.getEntitiesOfClass(
                ItemEntity.class,
                sweepBox,
                item -> item != null
                        && item.isAlive()
                        && (!item.getPersistentData().contains("PreventRemoteMovement")
                            || !item.getPersistentData().getBoolean("PreventRemoteMovement")));

        for (final ItemEntity drop : drops) {
            drop.setPos(worker.getX(), worker.getY() + 0.25D, worker.getZ());
            drop.setDeltaMovement(0.0D, 0.0D, 0.0D);
            drop.setNoPickUpDelay();
        }
    }
}
