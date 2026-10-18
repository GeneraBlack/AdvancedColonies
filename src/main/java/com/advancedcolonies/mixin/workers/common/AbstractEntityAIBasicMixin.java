package com.advancedcolonies.mixin.workers.common;

import com.advancedcolonies.workers.common.WorkerHutLogisticsHelper;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

/**
 * Thin Mixin hook on {@link AbstractEntityAIBasic} redirecting rack-by-rack pickup
 * position resolution to {@link WorkerHutLogisticsHelper}.
 */
@Mixin(value = AbstractEntityAIBasic.class, remap = false)
public abstract class AbstractEntityAIBasicMixin {

    @Redirect(
            method = "getNeededItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/tileentities/AbstractTileEntityColonyBuilding;getPositionOfChestWithItemStack(Ljava/util/function/Predicate;)Lnet/minecraft/core/BlockPos;"
            ),
            remap = false
    )
    private BlockPos advancedcolonies$resolveDirectHutPickupPosition(
            final AbstractTileEntityColonyBuilding tileEntity,
            final Predicate<ItemStack> itemPredicate) {
        return WorkerHutLogisticsHelper.resolvePickupPosition(tileEntity, itemPredicate);
    }
}
