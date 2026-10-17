package com.advancedcolonies.mixin.buildings.common;

import com.advancedcolonies.buildings.common.BuildingCapacityHelper;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link WorkerBuildingModule} delegating worker slot capacity
 * calculation to {@link BuildingCapacityHelper}.
 */
@Mixin(value = WorkerBuildingModule.class, remap = false)
public abstract class WorkerBuildingModuleMixin {

    @Inject(method = "getModuleMax", at = @At("RETURN"), cancellable = true, remap = false)
    private void advancedcolonies$scaleWorkerModuleMax(final CallbackInfoReturnable<Integer> cir) {
        final WorkerBuildingModule self = (WorkerBuildingModule) (Object) this;
        final int effective = BuildingCapacityHelper.computeWorkerModuleMax(self, cir.getReturnValue());
        if (effective != cir.getReturnValue()) {
            cir.setReturnValue(effective);
        }
    }
}
