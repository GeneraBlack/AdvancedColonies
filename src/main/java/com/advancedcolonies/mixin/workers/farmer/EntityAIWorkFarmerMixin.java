package com.advancedcolonies.mixin.workers.farmer;

import com.advancedcolonies.workers.farmer.AgricultureWorkHelper;
import com.advancedcolonies.workers.farmer.IFarmFieldCropRotation;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import com.minecolonies.core.colony.jobs.JobFarmer;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Thin Mixin hook on {@link EntityAIWorkFarmer} delegating enhanced harvest-and-replant,
 * spiral field scanning, right-click harvesting, crop rotation day tracking, and server-limited
 * crop drop scaling to {@link AgricultureWorkHelper}.
 */
@Mixin(value = EntityAIWorkFarmer.class, remap = false)
public abstract class EntityAIWorkFarmerMixin extends AbstractEntityAICrafting<JobFarmer, BuildingFarmer> {

    @Shadow
    private boolean didWork;

    @Shadow
    protected abstract BlockPos getSurfacePos(BlockPos position);

    @Shadow
    protected abstract BlockPos findHarvestableSurface(BlockPos position);

    protected EntityAIWorkFarmerMixin(@NotNull final JobFarmer job) {
        super(job);
    }

    @Inject(method = "getLevelDelay", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onGetLevelDelay(final CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(AgricultureWorkHelper.computeFarmerActionDelay(cir.getReturnValue()));
    }

    @Inject(method = "nextValidCell", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onNextValidCell(
            final FarmField farmField,
            final CallbackInfoReturnable<BlockPos> cir) {
        if (building != null && farmField != null) {
            if (farmField instanceof IFarmFieldCropRotation rotation
                    && worker != null
                    && worker.getCitizenColonyHandler() != null
                    && worker.getCitizenColonyHandler().getColonyOrRegister() != null) {
                rotation.advancedColonies$advanceDay(worker.getCitizenColonyHandler().getColonyOrRegister().getDay());
            }
            if (AgricultureWorkHelper.isSpiralFieldScanFixed()) {
                cir.setReturnValue(AgricultureWorkHelper.computeNextSpiralValidCell(building, farmField));
            }
        }
    }

    @Inject(method = "harvestIfAble", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onBeforeHarvestIfAble(
            final BlockPos position,
            final CallbackInfoReturnable<Boolean> cir) {
        if (position == null || building == null || world == null || worker == null) {
            return;
        }
        final BuildingExtensionsModule module = building.getFirstModuleOccurance(BuildingExtensionsModule.class);
        final IBuildingExtension ext = module != null ? module.getCurrentExtension() : null;
        if (ext instanceof FarmField farmField) {
            final BlockPos harvestableSurface = findHarvestableSurface(position);
            if (harvestableSurface != null
                    && AgricultureWorkHelper.tryRightClickHarvestCrop(world, worker, farmField, harvestableSurface)) {
                didWork = true;
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "harvestIfAble", at = @At("RETURN"))
    private void advancedColonies$onAfterHarvestIfAble(
            final BlockPos position,
            final CallbackInfoReturnable<Boolean> cir) {
        if (position == null || !Boolean.TRUE.equals(cir.getReturnValue()) || !didWork || building == null) {
            return;
        }
        final BuildingExtensionsModule module = building.getFirstModuleOccurance(BuildingExtensionsModule.class);
        final IBuildingExtension ext = module != null ? module.getCurrentExtension() : null;
        final FarmField farmField = ext instanceof FarmField ff ? ff : null;
        final BlockPos surfacePos = getSurfacePos(position);
        AgricultureWorkHelper.onAfterFarmerHarvest(world, worker, farmField, surfacePos);
    }

    @Inject(method = "increaseBlockDrops", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onIncreaseBlockDrops(
            final List<ItemStack> drops,
            final CallbackInfoReturnable<List<ItemStack>> cir) {
        final List<ItemStack> vanillaDrops = cir.getReturnValue();
        if (vanillaDrops != null) {
            cir.setReturnValue(AgricultureWorkHelper.scaleFarmerHarvestDrops(vanillaDrops, getPrimarySkillLevel()));
        }
    }
}

