package com.advancedcolonies.mixin.common.eating;

import com.advancedcolonies.buildings.restaurant.RestaurantSeatSelector;
import com.advancedcolonies.common.eating.CitizenEatingHelper;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.workers.deliveryman.DeliverymanLogisticsOptimizer;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.managers.interfaces.IRegisteredStructureManager;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.entity.ai.minimal.EntityAIEatTask;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.minecolonies.api.util.constant.CitizenConstants.FULL_SATURATION;

/**
 * Thin Mixin hook on {@link EntityAIEatTask} delegating load-balanced restaurant selection,
 * immediate eating after Cook service, clean chair dismounting, and Warehouse courier dining to
 * {@link RestaurantSeatSelector}, {@link CitizenEatingHelper}, and {@link DeliverymanLogisticsOptimizer}.
 */
@Mixin(value = EntityAIEatTask.class, remap = false)
public abstract class EntityAIEatTaskMixin {

    @Shadow
    @Final
    private EntityCitizen citizen;

    @Shadow
    private BlockPos restaurantPos;

    @Shadow
    private int waitingTicks;

    @Shadow
    private int timeOutWalking;

    @Shadow
    private boolean hasFood() {
        return false;
    }

    @Shadow
    private void reset() {
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void advancedColonies$onReset(final CallbackInfo ci) {
        CitizenEatingHelper.ensureDismountedFromChair(this.citizen);
    }

    @Inject(method = "eat", at = @At("RETURN"))
    private void advancedColonies$onEatReturn(final CallbackInfoReturnable<IState> cir) {
        if (cir.getReturnValue() == CitizenAIState.IDLE) {
            CitizenEatingHelper.ensureDismountedFromChair(this.citizen);
        }
    }

    @Inject(method = "goToEatingPlace", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onGoToEatingPlaceHead(
            final CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedEatingAI.get()) {
            return;
        }
        final ICitizenData citizenData = this.citizen.getCitizenData();
        if (citizenData != null && citizenData.getSaturation() >= FULL_SATURATION) {
            this.reset();
            citizenData.setJustAte(true);
            cir.setReturnValue(EntityAIEatTask.EatingState.DONE);
            return;
        }
        // If the Cook already served food into the citizen's inventory while seated in goToEatingPlace,
        // transition to EAT immediately instead of waiting 400 walking-timeout ticks (~35 seconds).
        if (this.hasFood()) {
            this.waitingTicks = 0;
            this.timeOutWalking = 0;
            cir.setReturnValue(EntityAIEatTask.EatingState.EAT);
        }
    }

    @Inject(method = "waitForFood", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onWaitForFoodHead(
            final CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedEatingAI.get()) {
            return;
        }
        final ICitizenData citizenData = this.citizen.getCitizenData();
        if (citizenData != null && citizenData.getSaturation() >= FULL_SATURATION) {
            this.reset();
            citizenData.setJustAte(true);
            cir.setReturnValue(EntityAIEatTask.EatingState.DONE);
        }
    }

    @Redirect(
            method = "waitForFood",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/colony/managers/interfaces/IRegisteredStructureManager;getBestBuilding(Lnet/minecraft/core/BlockPos;Ljava/lang/Class;)Lnet/minecraft/core/BlockPos;"
            ),
            remap = false
    )
    private BlockPos advancedColonies$preserveSelectedRestaurantInWaitForFood(
            final IRegisteredStructureManager manager,
            final BlockPos startPos,
            final Class<?> buildingClass) {
        if (AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedEatingAI.get()
                && this.restaurantPos != null
                && manager.getBuilding(this.restaurantPos) instanceof BuildingCook) {
            return this.restaurantPos;
        }
        return manager.getBestBuilding(startPos, BuildingCook.class);
    }

    @Inject(method = "getFoodYourself", at = @At("HEAD"))
    private void advancedColonies$onGetFoodYourselfHead(
            final CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        CitizenEatingHelper.ensureDismountedFromChair(this.citizen);
    }

    @Inject(method = "goToHut", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$allowCourierToEatAtWarehouse(
            final CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        final EntityAIEatTask.EatingState warehouseState =
                DeliverymanLogisticsOptimizer.tryEatAtWarehouse(this.citizen);
        if (warehouseState != null) {
            cir.setReturnValue(warehouseState);
        }
    }

    @Inject(method = "searchRestaurant", at = @At("RETURN"))
    private void advancedColonies$onSearchRestaurant(final CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedEatingAI.get()
                || !AdvancedColoniesConfig.CITIZEN_COMMON.preorderRestaurantTable.get()) {
            return;
        }

        final ICitizenData citizenData = this.citizen.getCitizenData();
        if (citizenData == null) {
            return;
        }

        final BlockPos searchFrom = citizenData.getWorkBuilding() != null
                ? citizenData.getWorkBuilding().getPosition()
                : citizenData.getHomeBuilding() != null
                        ? citizenData.getHomeBuilding().getPosition()
                        : this.citizen.blockPosition();

        final BlockPos balancedPos = RestaurantSeatSelector.selectLeastCrowdedRestaurant(this.citizen, searchFrom);
        if (balancedPos != null) {
            this.restaurantPos = balancedPos;
        }
    }
}
