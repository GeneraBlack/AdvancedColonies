package com.advancedcolonies.mixin.workers.common;

import com.advancedcolonies.workers.smelter.FurnaceAndCraftingHelper;
import com.minecolonies.api.util.ItemStackUtils;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link ItemStackUtils} delegating enchanted tool level
 * verification to {@link FurnaceAndCraftingHelper}.
 */
@Mixin(value = ItemStackUtils.class, remap = false)
public abstract class ItemStackUtilsEquipmentMixin {

    @Inject(method = "verifyEquipmentLevel", at = @At("RETURN"), cancellable = true, remap = false)
    private static void advancedcolonies$allowEarlyEnchantedTools(
            final ItemStack itemStack,
            final int equipmentLevel,
            final int minimalLevel,
            final int maximumLevel,
            final CallbackInfoReturnable<Boolean> cir) {
        final boolean allowed = FurnaceAndCraftingHelper.verifyEnchantedEquipmentLevel(
                itemStack,
                equipmentLevel,
                minimalLevel,
                maximumLevel,
                cir.getReturnValue());
        if (allowed != cir.getReturnValue()) {
            cir.setReturnValue(allowed);
        }
    }
}
