package com.advancedcolonies.mixin.buildings.residence;

import com.advancedcolonies.buildings.common.BuildingCapacityHelper;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link LivingBuildingModule} delegating residential bed
 * capacity scaling to {@link BuildingCapacityHelper}.
 */
@Mixin(value = LivingBuildingModule.class, remap = false)
public abstract class LivingBuildingModuleMixin {

    @Inject(method = "getModuleMax", at = @At("RETURN"), cancellable = true, remap = false)
    private void advancedcolonies$scaleResidenceBedMax(final CallbackInfoReturnable<Integer> cir) {
        final LivingBuildingModule self = (LivingBuildingModule) (Object) this;
        final int effective = BuildingCapacityHelper.computeResidenceBedMax(self, cir.getReturnValue());
        if (effective != cir.getReturnValue()) {
            cir.setReturnValue(effective);
        }
    }
}
