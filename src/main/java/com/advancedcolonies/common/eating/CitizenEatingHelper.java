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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Shared helper for citizen food evaluation, global food blacklists, nutrition multipliers,
 * and clean chair dismounting after meals.
 */
public final class CitizenEatingHelper {

    private CitizenEatingHelper() {
        // Utility class
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

        final int invSlot = FoodUtils.getBestFoodForCitizen(inventory, citizenData, menu);
        if (invSlot < 0) {
            return false;
        }

        final ItemStack stack = inventory.getStackInSlot(invSlot);
        if (stack.isEmpty()) {
            return false;
        }

        final boolean isMinecolFood = stack.getItem() instanceof IMinecoloniesFoodItem;
        final ICitizenFoodHandler foodHandler = citizenData.getCitizenFoodHandler();
        final ICitizenFoodHandler.CitizenFoodStats foodStats = foodHandler.getFoodHappinessStats();
        final int homeLevel = citizenData.getHomeBuilding() == null ? 0 : citizenData.getHomeBuilding().getBuildingLevelEquivalent();
        final int diversityReq = FoodUtils.getMinFoodDiversityRequirement(homeLevel);
        final int qualityReq = FoodUtils.getMinFoodQualityRequirement(homeLevel);
        final int localScore = foodHandler.checkLastEaten(stack.getItem()) * (isMinecolFood ? 1 : 2);

        return (localScore < 0 && isMinecolFood)
                || (localScore < 0 && foodStats.quality() > qualityReq * 2)
                || (isMinecolFood && foodStats.diversity() > diversityReq * 2);
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
