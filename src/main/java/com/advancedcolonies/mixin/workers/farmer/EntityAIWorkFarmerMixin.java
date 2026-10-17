package com.advancedcolonies.mixin.workers.farmer;

import com.advancedcolonies.workers.farmer.AgricultureWorkHelper;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import com.minecolonies.core.colony.jobs.JobFarmer;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIWorkFarmer} delegating enhanced harvest-and-replant
 * and per-tile delay scaling to {@link AgricultureWorkHelper}.
 */
@Mixin(value = EntityAIWorkFarmer.class, remap = false)
public abstract class EntityAIWorkFarmerMixin extends AbstractEntityAICrafting<JobFarmer, BuildingFarmer> {

    @Shadow
    private boolean didWork;

    @Shadow
    protected abstract BlockPos getSurfacePos(BlockPos position);

    protected EntityAIWorkFarmerMixin(@NotNull final JobFarmer job) {
        super(job);
    }

    @Inject(method = "getLevelDelay", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onGetLevelDelay(final CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(AgricultureWorkHelper.computeFarmerActionDelay(cir.getReturnValue()));
    }

    @Inject(method = "harvestIfAble", at = @At("RETURN"))
    private void advancedColonies$onAfterHarvestIfAble(
            final BlockPos position,
            final CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || !didWork || building == null) {
            return;
        }
        final BuildingExtensionsModule module = building.getFirstModuleOccurance(BuildingExtensionsModule.class);
        final IBuildingExtension ext = module != null ? module.getCurrentExtension() : null;
        final FarmField farmField = ext instanceof FarmField ff ? ff : null;
        final BlockPos surfacePos = getSurfacePos(position);
        AgricultureWorkHelper.onAfterFarmerHarvest(world, worker, farmField, surfacePos);
    }
}
