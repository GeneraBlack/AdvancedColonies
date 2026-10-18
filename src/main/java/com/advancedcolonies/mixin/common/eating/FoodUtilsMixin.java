package com.advancedcolonies.mixin.common.eating;

import com.advancedcolonies.common.eating.CitizenEatingHelper;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.util.FoodUtils;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Thin Mixin hook on {@link FoodUtils} delegating global food blacklist checks,
 * configurable food-level requirement bypass, nutrition multipliers, and Cook inventory
 * sufficiency checks to {@link CitizenEatingHelper}.
 */
@Mixin(value = FoodUtils.class, remap = false)
public abstract class FoodUtilsMixin {

    @Inject(method = "canEat", at = @At("HEAD"), cancellable = true)
    private static void advancedColonies$onCanEat(
            final ItemStack stack,
            final IBuilding homeBuilding,
            final IBuilding workBuilding,
            final CallbackInfoReturnable<Boolean> cir) {
        if (CitizenEatingHelper.isGloballyBlacklisted(stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canEatLevel", at = @At("HEAD"), cancellable = true)
    private static void advancedColonies$onCanEatLevel(
            final ItemStack stack,
            final int buildingLevel,
            final CallbackInfoReturnable<Boolean> cir) {
        if (CitizenEatingHelper.shouldIgnoreFoodLevelRequirements()) {
            cir.setReturnValue(CitizenEatingHelper.canEatIgnoringBuildingLevel(stack));
        }
    }

    @Inject(method = "getBestFoodForCitizen", at = @At("RETURN"), cancellable = true)
    private static void advancedColonies$onGetBestFoodForCitizenReturn(
            final InventoryCitizen inventoryCitizen,
            final ICitizenData citizenData,
            final Set<ItemStorage> menu,
            final CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() == -1) {
            final int fallbackSlot = CitizenEatingHelper.resolveBestFoodSlotFallback(
                    -1, inventoryCitizen, citizenData, menu);
            if (fallbackSlot != -1) {
                cir.setReturnValue(fallbackSlot);
            }
        }
    }

    @Inject(
            method = "getFoodValue(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;D)D",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void advancedColonies$onGetFoodValue(
            final ItemStack foodStack,
            final FoodProperties itemFood,
            final double researchBonus,
            final CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(CitizenEatingHelper.adjustFoodValue(cir.getReturnValueD(), foodStack));
    }

    @Inject(method = "hasBestOptionInInv", at = @At("HEAD"), cancellable = true)
    private static void advancedColonies$onHasBestOptionInInv(
            final InventoryCitizen inventoryCitizen,
            final ICitizenData citizenData,
            final Set<ItemStorage> menu,
            final IBuilding building,
            final CallbackInfoReturnable<Boolean> cir) {
        if (CitizenEatingHelper.hasSufficientFoodInInventory(inventoryCitizen, citizenData, menu)) {
            cir.setReturnValue(true);
        }
    }
}
