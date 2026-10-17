package com.advancedcolonies.mixin.workers.farmer;

import com.advancedcolonies.workers.farmer.AgricultureWorkHelper;
import com.minecolonies.api.colony.buildingextensions.plantation.IPlantationModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingPlantation;
import com.minecolonies.core.colony.jobs.JobPlanter;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkPlanter;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIWorkPlanter} delegating plantation harvest drop sweeping
 * to {@link AgricultureWorkHelper}.
 */
@Mixin(value = EntityAIWorkPlanter.class, remap = false)
public abstract class EntityAIWorkPlanterMixin extends AbstractEntityAICrafting<JobPlanter, BuildingPlantation> {

    @Shadow
    private IPlantationModule.PlantationModuleResult activeModuleResult;

    protected EntityAIWorkPlanterMixin(@NotNull final JobPlanter job) {
        super(job);
    }

    @Inject(
            method = "handleMiningAction",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/util/citizenutils/CitizenItemUtils;pickupItems(Lcom/minecolonies/api/entity/citizen/AbstractEntityCitizen;)V"))
    private void advancedColonies$onBeforePlanterPickupItems(
            final boolean isHarvest,
            final CallbackInfoReturnable<?> cir) {
        if (isHarvest && activeModuleResult != null) {
            AgricultureWorkHelper.onAfterPlanterHarvest(world, worker, activeModuleResult.getActionPosition());
        }
    }
}
