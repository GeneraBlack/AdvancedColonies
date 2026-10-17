package com.advancedcolonies.mixin.buildings.common;

import com.advancedcolonies.buildings.common.MinimumStockHelper;
import com.minecolonies.core.colony.buildings.modules.MinimumStockModule;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Thin Mixin hook on {@link MinimumStockModule} delegating minimum-stock
 * stack-size multiplication to {@link MinimumStockHelper}.
 */
@Mixin(value = MinimumStockModule.class, remap = false)
public abstract class MinimumStockModuleMixin {

    @Redirect(
            method = "onColonyTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I",
                    ordinal = 0
            )
    )
    private int advancedcolonies$usePreciseStockTargetInTick(final ItemStack stack) {
        return MinimumStockHelper.getEffectiveStackMultiplier(stack);
    }

    @Redirect(
            method = "alterItemsToBeKept",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"
            )
    )
    private int advancedcolonies$usePreciseStockTargetInKeepList(final ItemStack stack) {
        return MinimumStockHelper.getEffectiveStackMultiplier(stack);
    }
}
