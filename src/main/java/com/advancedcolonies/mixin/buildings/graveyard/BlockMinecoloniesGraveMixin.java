package com.advancedcolonies.mixin.buildings.graveyard;

import com.advancedcolonies.buildings.graveyard.GraveyardResurrectionHelper;
import com.minecolonies.core.blocks.BlockMinecoloniesGrave;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link BlockMinecoloniesGrave} to allow manual player resurrection of
 * fallen citizens using a configurable catalyst item.
 */
@Mixin(BlockMinecoloniesGrave.class)
public abstract class BlockMinecoloniesGraveMixin {

    /**
     * Intercepts item interaction on a grave block to attempt manual citizen resurrection.
     *
     * @param stack   the held item stack
     * @param state   the block state
     * @param worldIn the world level
     * @param pos     the grave block position
     * @param player  the interacting player
     * @param hand    the interaction hand
     * @param ray     the block hit result
     * @param cir     callback info returnable
     */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void advancedcolonies$tryManualResurrectionOnGrave(
            final ItemStack stack,
            final BlockState state,
            final Level worldIn,
            final BlockPos pos,
            final Player player,
            final InteractionHand hand,
            final BlockHitResult ray,
            final CallbackInfoReturnable<ItemInteractionResult> cir) {
        final ItemInteractionResult result = GraveyardResurrectionHelper.tryManualGraveResurrection(
                stack, worldIn, pos, player, hand);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
