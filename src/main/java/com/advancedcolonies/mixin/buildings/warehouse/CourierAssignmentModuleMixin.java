package com.advancedcolonies.mixin.buildings.warehouse;

import com.advancedcolonies.buildings.common.BuildingCapacityHelper;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link CourierAssignmentModule} delegating Warehouse courier
 * capacity scaling to {@link BuildingCapacityHelper}.
 */
@Mixin(value = CourierAssignmentModule.class, remap = false)
public abstract class CourierAssignmentModuleMixin extends AbstractBuildingModule {

    @Inject(method = "getModuleMax", at = @At("RETURN"), cancellable = true, remap = false)
    private void advancedcolonies$scaleWarehouseCourierMax(final CallbackInfoReturnable<Integer> cir) {
        if (this.building != null) {
            final int effective = BuildingCapacityHelper.computeWarehouseCourierMax(
                    this.building.getBuildingLevel(),
                    cir.getReturnValue());
            if (effective != cir.getReturnValue()) {
                cir.setReturnValue(effective);
            }
        }
    }
}
