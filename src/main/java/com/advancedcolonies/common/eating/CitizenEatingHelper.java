package com.advancedcolonies.common.eating;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.CitizenCommonConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenFoodHandler;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.items.IMinecoloniesFoodItem;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.items.ItemCrop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Shared helper for citizen food evaluation, global food blacklists, configurable food-level bypass,
 * nutrition multipliers, and clean chair dismounting after meals.
 */
public final class CitizenEatingHelper {

    /** Maximum building level in MineColonies (Level 5). */
    public static final int MAX_BUILDING_FOOD_LEVEL = 5;

    /** Maximum MineColonies food tier (Tier 3). */
    public static final int MAX_FOOD_TIER = 3;

    /** Maximum food quality and diversity stats (10/10) when food level restrictions are ignored. */
    public static final ICitizenFoodHandler.CitizenFoodStats MAX_FOOD_HAPPINESS_STATS =
            new ICitizenFoodHandler.CitizenFoodStats(10, 10);

    private CitizenEatingHelper() {
        // Utility class
    }

    /**
     * Checks whether building/hut food level restrictions should be ignored.
     */
    public static boolean shouldIgnoreFoodLevelRequirements() {
        return AdvancedColoniesConfig.CITIZEN_COMMON.ignoreFoodLevelRequirements.get();
    }

    /**
     * Evaluates whether {@code stack} is edible when {@code ignoreFoodLevelRequirements} is enabled,
     * bypassing MineColonies' {@code nutrition >= buildingLevel + 1} restriction for Level 3–5 citizens
     * while still rejecting raw crops and globally blacklisted items.
     *
     * @param stack the candidate food stack.
     * @return {@code true} if the item is a valid edible food item.
     */
    public static boolean canEatIgnoringBuildingLevel(@NotNull final ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof ItemCrop || isGloballyBlacklisted(stack)) {
            return false;
        }
        return stack.getItem().getFoodProperties(stack, null) != null;
    }

    /**
     * Fallback for {@link FoodUtils#getBestFoodForCitizen(InventoryCitizen, ICitizenData, Set)} when
     * vanilla returns {@code -1} because a Level 3–5 citizen's {@code diversityReq}, {@code qualityReq},
     * or active {@code restaurant} menu filter rejected a valid edible food item already in their inventory.
     *
     * @param originalSlot the slot returned by vanilla {@code getBestFoodForCitizen}.
     * @param inventory    the inventory being searched.
     * @param citizenData  the citizen data.
     * @param menu         optional restaurant menu filter.
     * @return a valid food slot index, or {@code -1} if no edible food is present.
     */
    public static int resolveBestFoodSlotFallback(
            final int originalSlot,
            @Nullable final InventoryCitizen inventory,
            @Nullable final ICitizenData citizenData,
            @Nullable final Set<ItemStorage> menu) {
        if (originalSlot != -1 || inventory == null || citizenData == null) {
            return originalSlot;
        }
        if (!shouldIgnoreFoodLevelRequirements()
                && !AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedEatingAI.get()) {
            return -1;
        }

        int bestSlot = findBestEdibleSlot(inventory, citizenData, menu);
        if (bestSlot == -1 && menu != null && inventory == citizenData.getInventory()) {
            // When checking the citizen's OWN inventory (e.g. inside EntityAIEatTask#hasFood() while
            // this.restaurant != null), do not reject valid edible food the citizen is already carrying
            // just because it is not on the current restaurant's menu (which would otherwise trigger
            // BETTER_FOOD complaints and abort eating into an infinite loop).
            bestSlot = findBestEdibleSlot(inventory, citizenData, null);
        }
        return bestSlot;
    }

    private static int findBestEdibleSlot(
            @NotNull final InventoryCitizen inventory,
            @NotNull final ICitizenData citizenData,
            @Nullable final Set<ItemStorage> menu) {
        final ICitizenFoodHandler foodHandler = citizenData.getCitizenFoodHandler();
        int bestSlot = -1;
        int bestScore = Integer.MAX_VALUE;
        int bestNutrition = -1;

        final int slots = inventory.getSlots();
        for (int i = 0; i < slots; i++) {
            final ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (menu != null && !menu.contains(new ItemStorage(stack))) {
                continue;
            }
            if (!FoodUtils.canEat(stack, citizenData.getHomeBuilding(), citizenData.getWorkBuilding())) {
                continue;
            }

            final boolean isMinecolFood = stack.getItem() instanceof IMinecoloniesFoodItem;
            final int localScore = foodHandler != null
                    ? foodHandler.checkLastEaten(stack.getItem()) * (isMinecolFood ? 1 : 2)
                    : 0;
            final FoodProperties props = stack.getItem().getFoodProperties(stack, null);
            final int nutrition = props != null ? props.nutrition() : 0;

            if (localScore < bestScore || (localScore == bestScore && nutrition > bestNutrition)) {
                bestScore = localScore;
                bestNutrition = nutrition;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    /**
     * Checks whether a food stack is forbidden by the global food blacklist in {@link CitizenCommonConfig}.
     *
     * @param stack the food item stack to test.
     * @return {@code true} if the item is globally blacklisted from citizen consumption.
     */
    public static boolean isGloballyBlacklisted(@NotNull final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        final List<? extends String> blacklist = AdvancedColoniesConfig.CITIZEN_COMMON.globalFoodBlacklist.get();
        if (blacklist.isEmpty()) {
            return false;
        }
        final ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && blacklist.contains(key.toString());
    }

    /**
     * Adjusts the saturation/nutrition value granted when a citizen consumes {@code foodStack}.
     *
     * @param baseFoodValue the vanilla MineColonies food value.
     * @param foodStack     the consumed food stack.
     * @return the scaled food value.
     */
    public static double adjustFoodValue(final double baseFoodValue, @NotNull final ItemStack foodStack) {
        if (baseFoodValue <= 0.0D || foodStack.isEmpty()) {
            return baseFoodValue;
        }
        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        final double multiplier = (foodStack.getItem() instanceof IMinecoloniesFoodItem)
                ? cfg.minecoloniesFoodMultiplier.get()
                : cfg.vanillaFoodMultiplier.get();
        return baseFoodValue * multiplier;
    }

    /**
     * Checks whether the given inventory (e.g. a Cook's inventory) already holds a valid food item
     * from {@code menu} that satisfies the target citizen's quality and diversity requirements,
     * avoiding redundant trips to the restaurant racks.
     *
     * @param inventory   the inventory to inspect.
     * @param citizenData the citizen to be fed.
     * @param menu        the active restaurant menu, or {@code null}.
     * @return {@code true} if the inventory already contains a good-enough food option.
     */
    public static boolean hasSufficientFoodInInventory(
            @NotNull final InventoryCitizen inventory,
            @NotNull final ICitizenData citizenData,
            @Nullable final Set<ItemStorage> menu) {

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedRestaurantCookAI.get()) {
            return false;
        }

        final ICitizenFoodHandler foodHandler = citizenData.getCitizenFoodHandler();
        final ICitizenFoodHandler.CitizenFoodStats foodStats = foodHandler.getFoodHappinessStats();
        final int homeLevel = citizenData.getHomeBuilding() == null
                ? 0
                : citizenData.getHomeBuilding().getBuildingLevelEquivalent();
        final int diversityReq = FoodUtils.getMinFoodDiversityRequirement(homeLevel);
        final int qualityReq = FoodUtils.getMinFoodQualityRequirement(homeLevel);

        final int slots = inventory.getSlots();
        for (int i = 0; i < slots; i++) {
            final ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (menu != null && !menu.contains(new ItemStorage(stack))) {
                continue;
            }
            if (!FoodUtils.canEat(stack, citizenData.getHomeBuilding(), citizenData.getWorkBuilding())) {
                continue;
            }

            final boolean isMinecolFood = stack.getItem() instanceof IMinecoloniesFoodItem;
            final int localScore = foodHandler.checkLastEaten(stack.getItem()) * (isMinecolFood ? 1 : 2);
            if ((localScore < 0 && isMinecolFood)
                    || (localScore < 0 && foodStats.quality() > qualityReq * 2)
                    || (isMinecolFood && foodStats.diversity() > diversityReq * 2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ensures a citizen cleanly dismounts any {@code SittingEntity} chair when resetting or finishing eating.
     *
     * @param citizen the citizen entity.
     */
    public static void ensureDismountedFromChair(@NotNull final EntityCitizen citizen) {
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedEatingAI.get()) {
            return;
        }
        if (citizen.isPassenger()) {
            citizen.stopRiding();
        }
    }

    /**
     * Fixes the copy-paste bug in {@code CitizenData#needsBetterFood()} where {@code slotBadFood}
     * and {@code slotGoodFood} used the exact same {@code FoodUtils.canEat(...)} predicate,
     * causing {@code slotBadFood != -1 && slotGoodFood == -1} to always evaluate to {@code false}.
     *
     * @param citizenData the citizen data to evaluate
     * @return {@code true} if the citizen only carries low-tier/disallowed edible food and no valid tier food
     */
    public static boolean evaluateNeedsBetterFood(@NotNull final ICitizenData citizenData) {
        if (shouldIgnoreFoodLevelRequirements()) {
            return false;
        }
        if (citizenData.getHomeBuilding() == null || citizenData.getInventory() == null) {
            return false;
        }
        final int slotBadFood = com.minecolonies.api.util.InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(
                citizenData.getInventory(),
                stack -> FoodUtils.EDIBLE.test(stack)
                        && !FoodUtils.canEat(stack, citizenData.getHomeBuilding(), citizenData.getWorkBuilding()));
        final int slotGoodFood = com.minecolonies.api.util.InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(
                citizenData.getInventory(),
                stack -> FoodUtils.canEat(stack, citizenData.getHomeBuilding(), citizenData.getWorkBuilding()));
        return slotBadFood != -1 && slotGoodFood == -1;
    }
}
