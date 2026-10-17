package com.advancedcolonies.mixin.buildings.warehouse;

import com.advancedcolonies.buildings.common.BuildingCapacityHelper;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.CourierAssignmentModuleView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin client-side Mixin hook on {@link CourierAssignmentModuleView} ensuring the
 * Warehouse GUI displays the scaled courier capacity.
 */
@Mixin(value = CourierAssignmentModuleView.class, remap = false)
public abstract class CourierAssignmentModuleViewMixin extends AbstractBuildingModuleView {

    @Inject(method = "getMaxInhabitants", at = @At("RETURN"), cancellable = true, remap = false)
    private void advancedcolonies$scaleClientCourierMax(final CallbackInfoReturnable<Integer> cir) {
        if (this.buildingView != null) {
            final int effective = BuildingCapacityHelper.computeWarehouseCourierMax(
                    this.buildingView.getBuildingLevel(),
                    cir.getReturnValue());
            if (effective != cir.getReturnValue()) {
                cir.setReturnValue(effective);
            }
        }
    }
}
