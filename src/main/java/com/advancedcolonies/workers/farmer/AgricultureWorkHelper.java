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

    /**
     * Checks whether farmland trampling by a colony citizen or visitor should be prevented.
     * <p>
     * In vanilla MineColonies, unlocking the {@code SOFT_SHOES} University research only prevents
     * {@code JobFarmer} from trampling crops—meaning Guards, Couriers, Builders, and Visitors
     * walking across fields still destroy farmland. When {@code extendSoftShoesToAllCitizens} is
     * enabled, unlocking {@code SOFT_SHOES} protects farmland from all colony citizens.
     * </p>
     *
     * @param entity the entity stepping/falling onto farmland
     * @param pos    the farmland block position
     * @return {@code true} if the trample event should be cancelled
     */
    public static boolean shouldPreventCitizenFarmlandTrample(
            @Nullable final net.minecraft.world.entity.Entity entity,
            @Nullable final BlockPos pos) {

        if (!(entity instanceof AbstractEntityCitizen citizen) || citizen.level().isClientSide()) {
            return false;
        }

        if (AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.alwaysProtectColonyFarmland.get()) {
            return true;
        }

        if (!AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.extendSoftShoesToAllCitizens.get()) {
            return false;
        }

        com.minecolonies.api.colony.IColony colony = citizen.getCitizenColonyHandler() != null
                ? citizen.getCitizenColonyHandler().getColonyOrRegister()
                : null;
        if (colony == null && pos != null) {
            colony = com.minecolonies.api.colony.IColonyManager.getInstance().getIColony(citizen.level(), pos);
        }

        if (colony != null && colony.getResearchManager() != null && colony.getResearchManager().getResearchEffects() != null) {
            return colony.getResearchManager()
                    .getResearchEffects()
                    .getEffectStrength(com.minecolonies.api.research.util.ResearchConstants.SOFT_SHOES) > 0;
        }

        return false;
    }

    /**
     * Checks whether the division-by-zero-free spiral field scan ({@code nextValidCell}) is enabled.
     *
     * @return {@code true} if enabled
     */
    public static boolean isSpiralFieldScanFixed() {
        return AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.fixFarmerSpiralFieldScan.get();
    }

    /**
     * Computes the next valid spiral cell offset within {@code farmField} without division-by-zero hazards.
     *
     * @param building  the Farmer Hut building
     * @param farmField the target farm field
     * @return the next relative {@link BlockPos} offset, or {@code null} when the spiral scan completes
     */
    @Nullable
    public static BlockPos computeNextSpiralValidCell(
            @NotNull final com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer building,
            @NotNull final FarmField farmField) {

        if (building.getWorkingOffset() == null) {
            building.setWorkingOffset(BlockPos.ZERO);
        }

        int x = building.getWorkingOffset().getX();
        int z = building.getWorkingOffset().getZ();

        do {
            if (z > FarmField.MAX_RANGE) {
                return null;
            }

            final boolean onDiagonal = Math.abs(x) == Math.abs(z);
            final boolean nonNegativeX = x >= 0;
            final boolean positiveZ = z > 0;
            if (onDiagonal) {
                if (nonNegativeX) {
                    z++;
                } else if (positiveZ) {
                    z--;
                } else {
                    x++;
                }
                continue;
            }

            if (Math.abs(x) > Math.abs(z)) {
                if (nonNegativeX) {
                    z++;
                } else {
                    z--;
                }
            } else {
                if (positiveZ) {
                    x--;
                } else {
                    x++;
                }
            }
        } while (-z > farmField.getRadius(net.minecraft.core.Direction.NORTH)
                || x > farmField.getRadius(net.minecraft.core.Direction.EAST)
                || z > farmField.getRadius(net.minecraft.core.Direction.SOUTH)
                || -x > farmField.getRadius(net.minecraft.core.Direction.WEST));

        return new BlockPos(x, 0, z);
    }

    /**
     * Attempts to harvest a mature crop or bush at {@code surfacePos.above()} via right-click interaction
     * using a {@code FakePlayer} so compatible crops reset their age instead of breaking completely.
     *
     * @param world      the server level
     * @param worker     the Farmer citizen entity
     * @param farmField  the active farm field
     * @param surfacePos the farmland surface block position
     * @return {@code true} if right-click harvesting succeeded and consumed the action
     */
    public static boolean tryRightClickHarvestCrop(
            @Nullable final net.minecraft.server.level.ServerLevel world,
            @Nullable final AbstractEntityCitizen worker,
            @Nullable final FarmField farmField,
            @Nullable final BlockPos surfacePos) {

        if (!AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.enableFarmerRightClickHarvest.get()
                || world == null
                || worker == null
                || farmField == null
                || surfacePos == null) {
            return false;
        }

        final BlockPos cropPos = surfacePos.above();
        final net.minecraft.world.level.block.state.BlockState state = world.getBlockState(cropPos);
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.BushBlock)
                && !(state.getBlock() instanceof CropBlock)) {
            return false;
        }

        final net.minecraft.world.phys.BlockHitResult hitResult = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(cropPos),
                net.minecraft.core.Direction.UP,
                cropPos,
                false);
        final net.neoforged.neoforge.common.util.FakePlayer fakePlayer =
                net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(world);
        final ItemStack cloneSeed = state.getCloneItemStack(hitResult, world, cropPos, fakePlayer);

        if (farmField.getSeed().isEmpty() || farmField.getSeed().getItem() != cloneSeed.getItem()) {
            return false;
        }

        final net.minecraft.world.InteractionResult result = state.useWithoutItem(world, fakePlayer, hitResult);
        if (result.consumesAction()) {
            sweepNearbyDropsToWorker(world, worker, cropPos, 3.0D);
            CitizenItemUtils.pickupItems(worker);
            return true;
        }
        return false;
    }

    /**
     * Scales harvested crop drops according to the Farmer's Primary Skill and {@code farmerSkillHarvestBonusStep},
     * strictly clamped to the server limit {@code farmerMaxHarvestMultiplier}.
     *
     * @param baseDrops         the drops already processed by vanilla {@code increaseBlockDrops}
     * @param primarySkillLevel the Farmer's Primary Skill level
     * @return the scaled crop drops list respecting the server limit
     */
    @NotNull
    public static List<ItemStack> scaleFarmerHarvestDrops(
            @NotNull final List<ItemStack> baseDrops,
            final int primarySkillLevel) {

        final int step = AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.farmerSkillHarvestBonusStep.get();
        final int serverMaxMultiplier = Math.max(1, Math.min(16,
                AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.farmerMaxHarvestMultiplier.get()));

        if (step <= 0 || serverMaxMultiplier <= 1 || baseDrops.isEmpty()) {
            return baseDrops;
        }

        final int skillMultiplier = 1 + Math.max(0, primarySkillLevel) / step;
        final int effectiveMultiplier = Math.max(1, Math.min(skillMultiplier, serverMaxMultiplier));
        if (effectiveMultiplier <= 1) {
            return baseDrops;
        }

        for (final ItemStack stack : baseDrops) {
            if (!stack.isEmpty()) {
                stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() * effectiveMultiplier));
            }
        }
        return baseDrops;
    }

    /**
     * Checks whether {@code MinecoloniesCropBlock#updateShape} survival fix is enabled.
     *
     * @return {@code true} if enabled
     */
    public static boolean isMinecoloniesCropSurvivalFixed() {
        return AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.fixMinecoloniesCropBlockSurvival.get();
    }

    /**
     * Evaluates whether a {@link MinecoloniesCropBlock} at {@code pos} can survive on the block below it.
     *
     * @param level             the level reader
     * @param pos               the crop position
     * @param preferredFarmland the crop's preferred farmland block
     * @return {@code true} if the soil below is valid and light is sufficient
     */
    public static boolean canMinecoloniesCropSurviveOnFarmland(
            @NotNull final net.minecraft.world.level.LevelReader level,
            @NotNull final BlockPos pos,
            @Nullable final Block preferredFarmland) {

        final BlockPos belowPos = pos.below();
        final Block belowBlock = level.getBlockState(belowPos).getBlock();
        final boolean validSoil = belowBlock == preferredFarmland
                || belowBlock instanceof com.minecolonies.core.blocks.MinecoloniesFarmland;
        return validSoil && (level.getRawBrightness(pos, 0) >= 8 || level.canSeeSky(pos));
    }

    /**
     * Checks whether multi-season crop rotation on {@link FarmField} is enabled.
     *
     * @return {@code true} if enabled
     */
    public static boolean isCropRotationEnabled() {
        return AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.enableFarmFieldCropRotation.get();
    }

    /**
     * Returns the strict server limit on the maximum number of crop rotation seasons per {@link FarmField}.
     *
     * @return clamped max seasons (1..8)
     */
    public static int getClampedMaxSeasons() {
        return Math.max(1, Math.min(8, AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.cropRotationMaxSeasons.get()));
    }

    /**
     * Returns the strict server limit on the maximum duration in days of a single crop rotation season.
     *
     * @return clamped max season days (1..365)
     */
    public static int getClampedMaxSeasonDays() {
        return Math.max(1, Math.min(365, AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.cropRotationMaxSeasonDays.get()));
    }
}

