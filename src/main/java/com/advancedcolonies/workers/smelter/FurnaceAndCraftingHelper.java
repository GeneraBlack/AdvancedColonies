package com.advancedcolonies.workers.smelter;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.WorkerFurnaceAndCraftConfig;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.FurnaceUserModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBaker;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.Constants.FUEL_SLOT;
import static com.minecolonies.api.util.constant.Constants.RESULT_SLOT;
import static com.minecolonies.api.util.constant.Constants.SMELTABLE_SLOT;

/**
 * Provides furnace acceleration, automatic output extraction on refill,
 * multi-furnace batching, and early enchanted tool verification for colony workers.
 */
public final class FurnaceAndCraftingHelper {

    private FurnaceAndCraftingHelper() {
    }

    /**
     * Checks whether enhanced furnace management is enabled for the given building type.
     *
     * @param building the worker's building
     * @return {@code true} if enhanced furnace logic is enabled for this building
     */
    public static boolean isEnhancedFurnaceEnabledFor(@Nullable final AbstractBuilding building) {
        if (building == null) {
            return false;
        }
        final WorkerFurnaceAndCraftConfig cfg = AdvancedColoniesConfig.FURNACE_AND_CRAFT;
        if (building instanceof BuildingKitchen) {
            return cfg.enableEnhancedCookhouseChefAI.get();
        }
        if (building instanceof BuildingBaker) {
            return cfg.enableEnhancedBakerAI.get();
        }
        return cfg.enableEnhancedSmelteryAI.get();
    }

    /**
     * Accelerates all lit furnaces registered to the given building based on the worker's skill level.
     *
     * @param building   the worker's building
     * @param skillLevel the relevant worker skill level
     */
    public static void accelerateBuildingFurnaces(
            @Nullable final AbstractBuilding building,
            final int skillLevel) {
        if (building == null || !AdvancedColoniesConfig.FURNACE_AND_CRAFT.enableFurnaceAcceleration.get()) {
            return;
        }

        final FurnaceUserModule furnaceModule = building.getModule(BuildingModules.FURNACE);
        if (furnaceModule == null) {
            return;
        }

        final Level world = building.getColony().getWorld();
        if (world == null) {
            return;
        }

        final int extraTicks = Math.max(1, 1 + (skillLevel / 5) * 2);
        for (final BlockPos pos : furnaceModule.getFurnaces()) {
            if (WorldUtil.isBlockLoaded(world, pos)) {
                final BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof FurnaceBlockEntity furnace && isFurnaceLit(entity.getBlockState())) {
                    for (int i = 0; i < extraTicks && isFurnaceLit(entity.getBlockState()); i++) {
                        FurnaceBlockEntity.serverTick(world, pos, entity.getBlockState(), furnace);
                    }
                }
            }
        }
    }

    /**
     * Checks whether a furnace block state has {@link AbstractFurnaceBlock#LIT} set to true.
     */
    private static boolean isFurnaceLit(final BlockState state) {
        return state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT);
    }

    /**
     * Automatically extracts finished items from a furnace's {@code RESULT_SLOT} into the worker's
     * inventory when the worker visits the furnace to refill fuel or input materials.
     *
     * @param furnace the furnace block entity being refilled
     * @param worker  the worker citizen entity
     */
    public static void tryAutoExtractOnFill(
            @Nullable final FurnaceBlockEntity furnace,
            @Nullable final AbstractEntityCitizen worker) {
        if (furnace == null || worker == null) {
            return;
        }
        if (!AdvancedColoniesConfig.FURNACE_AND_CRAFT.autoExtractOnFurnaceFill.get()) {
            return;
        }

        final ItemStack resultStack = furnace.getItem(RESULT_SLOT);
        if (!ItemStackUtils.isEmpty(resultStack)) {
            InventoryUtils.transferItemStackIntoNextFreeSlotInItemHandler(
                    new InvWrapper(furnace),
                    RESULT_SLOT,
                    worker.getInventoryCitizen());
        }
    }

    /**
     * Finds a furnace with fuel and an empty input slot, even if its output slot still contains items
     * (which will be auto-extracted on arrival when {@code autoExtractOnFurnaceFill} is enabled).
     *
     * @param building the worker's building
     * @param world    the level
     * @return a candidate furnace position, or {@code null} if none found
     */
    @Nullable
    public static BlockPos findRefillableFurnaceWithFuel(
            @Nullable final AbstractBuilding building,
            @Nullable final Level world) {
        if (building == null || world == null) {
            return null;
        }
        if (!AdvancedColoniesConfig.FURNACE_AND_CRAFT.autoExtractOnFurnaceFill.get()
                && !isEnhancedFurnaceEnabledFor(building)) {
            return null;
        }

        final FurnaceUserModule module = building.getModule(BuildingModules.FURNACE);
        if (module == null) {
            return null;
        }

        for (final BlockPos pos : module.getFurnaces()) {
            if (WorldUtil.isBlockLoaded(world, pos)) {
                final BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof FurnaceBlockEntity furnace) {
                    if (ItemStackUtils.isEmpty(furnace.getItem(SMELTABLE_SLOT))
                            && !ItemStackUtils.isEmpty(furnace.getItem(FUEL_SLOT))) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Computes the number of actions a furnace-using worker should perform before dumping inventory,
     * allowing the worker to service all furnaces in the building in a single pass.
     *
     * @param building       the worker's building
     * @param vanillaActions the original vanilla action limit (typically 1)
     * @return the effective action count before dumping
     */
    public static int computeFurnaceActionsBeforeDump(
            @Nullable final AbstractBuilding building,
            final int vanillaActions) {
        if (!isEnhancedFurnaceEnabledFor(building)) {
            return vanillaActions;
        }

        final FurnaceUserModule module = building.getFirstModuleOccurance(FurnaceUserModule.class);
        if (module == null || module.getFurnaces().isEmpty()) {
            return vanillaActions;
        }

        return Math.max(vanillaActions, module.getFurnaces().size() * 2);
    }

    /**
     * Evaluates whether a tool or armor item stack satisfies the building's equipment level requirement
     * when {@code allowEarlyEnchantedTools} is enabled.
     *
     * @param itemStack      the equipment item stack
     * @param equipmentLevel the base material level of the equipment
     * @param minimalLevel   the minimum required level for the task
     * @param maximumLevel   the maximum allowed level for the worker's hut
     * @param vanillaResult  the original vanilla verification result
     * @return {@code true} if the equipment is allowed
     */
    public static boolean verifyEnchantedEquipmentLevel(
            final ItemStack itemStack,
            final int equipmentLevel,
            final int minimalLevel,
            final int maximumLevel,
            final boolean vanillaResult) {
        if (vanillaResult || !AdvancedColoniesConfig.FURNACE_AND_CRAFT.allowEarlyEnchantedTools.get()) {
            return vanillaResult;
        }

        final int maxEnchantBonus = ItemStackUtils.getMaxEnchantmentLevel(itemStack);
        if (equipmentLevel + maxEnchantBonus < minimalLevel) {
            return false;
        }

        final double scale = AdvancedColoniesConfig.FURNACE_AND_CRAFT.enchantLevelToHutScale.get();
        final int scaledEnchantCost = (int) Math.floor(maxEnchantBonus * scale);
        return (equipmentLevel + scaledEnchantCost) <= maximumLevel;
    }

    /**
     * Checks whether continuous batch crafting is enabled so that crafters stay in {@code CRAFT}
     * state between consecutive items of a multi-item batch when ingredients are already in inventory.
     *
     * @return {@code true} if continuous batch crafting is enabled
     */
    public static boolean isContinuousBatchCraftingEnabled() {
        return AdvancedColoniesConfig.FURNACE_AND_CRAFT.enableContinuousBatchCrafting.get();
    }

    /**
     * Computes smooth required crafting progress across all skill levels, fixing vanilla's
     * integer-division operator precedence truncation ({@code 10 / (jobModifier + 1) * 3})
     * and clamping to at least {@code 1} hit so progress never drops to {@code 0}.
     *
     * @param skillLevel         the worker's relevant craft-speed skill level
     * @param progressMultiplier vanilla {@code PROGRESS_MULTIPLIER} (10)
     * @param hittingTime        vanilla {@code HITTING_TIME} (3)
     * @param maxLevel           vanilla {@code MAX_LEVEL} (50)
     * @return the required progress count (at least 1)
     */
    public static int computeSmoothRequiredCraftingProgress(
            final int skillLevel,
            final int progressMultiplier,
            final int hittingTime,
            final int maxLevel) {
        final double effectiveDivisor = Math.min((skillLevel / 2.0D) + 1.0D, (double) maxLevel);
        final int required = (int) Math.round((double) (progressMultiplier * hittingTime) / Math.max(1.0D, effectiveDivisor));
        return Math.max(1, required);
    }
}
