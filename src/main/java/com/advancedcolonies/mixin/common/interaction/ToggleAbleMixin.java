package com.advancedcolonies.mixin.common.interaction;

import com.advancedcolonies.common.interaction.CitizenInteractionHelper;
import com.minecolonies.core.entity.ai.minimal.EntityAIInteractToggleAble;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIInteractToggleAble.ToggleAble} delegating to
 * {@link CitizenInteractionHelper#canOpenToggleableBlock(BlockState, boolean)} so citizens
 * do not slam already-open doors and fence gates shut while walking through them.
 */
@Mixin(value = EntityAIInteractToggleAble.ToggleAble.class, remap = false)
public abstract class ToggleAbleMixin {

    @Inject(
            method = "canOpen",
            at = @At("RETURN"),
            cancellable = true
    )
    private void advancedcolonies$preventSlammingOpenDoorsAndGates(
            final BlockState state,
            final CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(CitizenInteractionHelper.canOpenToggleableBlock(state, cir.getReturnValue()));
    }
}
