package com.advancedcolonies.mixin.buildings.builder;

import com.advancedcolonies.workers.builder.StructureWorkModeHelper;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.workorders.AbstractWorkOrder;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link WorkOrderBuilding} allowing Builders to claim building work orders
 * beyond the default 100-block distance limit when {@code builderAcceptOrdersAnywhere} is enabled.
 */
@Mixin(value = WorkOrderBuilding.class, remap = false)
public abstract class WorkOrderBuildingMixin extends AbstractWorkOrder {

    @Inject(method = "canBuild", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onCanBuild(
            final IBuilding building,
            final CallbackInfoReturnable<Boolean> cir) {
        if (StructureWorkModeHelper.canBuilderAcceptOrdersAnywhere()) {
            cir.setReturnValue(building instanceof BuildingBuilder
                    && canBuildIgnoringDistance(building, building.getPosition(), building.getBuildingLevel()));
        }
    }

    @Inject(method = "tooFarFromAnyBuilder", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onTooFarFromAnyBuilder(
            final IColony colony,
            final int level,
            final CallbackInfoReturnable<Boolean> cir) {
        if (StructureWorkModeHelper.canBuilderAcceptOrdersAnywhere() && colony != null) {
            final boolean noAssignedBuilder = colony.getServerBuildingManager()
                    .getBuildings()
                    .values()
                    .stream()
                    .noneMatch(b -> b instanceof BuildingBuilder && !b.getAllAssignedCitizen().isEmpty());
            cir.setReturnValue(noAssignedBuilder);
        }
    }
}
