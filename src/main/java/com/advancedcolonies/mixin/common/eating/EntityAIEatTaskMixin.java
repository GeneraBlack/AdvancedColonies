package com.advancedcolonies.mixin.common.eating;

import com.advancedcolonies.buildings.restaurant.RestaurantSeatSelector;
import com.advancedcolonies.common.eating.CitizenEatingHelper;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.minimal.EntityAIEatTask;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIEatTask} delegating load-balanced restaurant selection
 * and clean chair dismounting to {@link RestaurantSeatSelector} and {@link CitizenEatingHelper}.
 */
@Mixin(value = EntityAIEatTask.class, remap = false)
public abstract class EntityAIEatTaskMixin {

    @Shadow
    @Final
    private EntityCitizen citizen;

    @Shadow
    private BlockPos restaurantPos;

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
