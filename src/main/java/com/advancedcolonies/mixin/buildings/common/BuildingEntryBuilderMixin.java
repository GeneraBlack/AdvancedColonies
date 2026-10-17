package com.advancedcolonies.mixin.buildings.common;

import com.advancedcolonies.buildings.common.MinimumStockHelper;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Thin Mixin hook on {@link BuildingEntry.Builder} attaching universal
 * Minimum Stock and Warehouse food-menu modules during building registration.
 */
@Mixin(value = BuildingEntry.Builder.class, remap = false)
public abstract class BuildingEntryBuilderMixin {

    @SuppressWarnings("rawtypes")
    @Shadow(remap = false)
    private List<BuildingEntry.ModuleProducer> buildingModuleProducers;

    @Shadow(remap = false)
    private ResourceLocation registryName;

    @Inject(method = "createBuildingEntry", at = @At("HEAD"), remap = false)
    private void advancedcolonies$attachUniversalModules(final CallbackInfoReturnable<BuildingEntry> cir) {
        MinimumStockHelper.attachUniversalModules(this.registryName, this.buildingModuleProducers);
    }
}
