package com.advancedcolonies.mixin.common.navigation;

import com.advancedcolonies.common.navigation.NavigationHeuristicEvaluator;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveCloseToXNearY;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link PathJobMoveCloseToXNearY} delegating near-target 3D heuristic
 * evaluation to {@link NavigationHeuristicEvaluator}.
 */
@Mixin(value = PathJobMoveCloseToXNearY.class, remap = false)
public abstract class PathJobMoveCloseToXNearYMixin {

    @Shadow
    @Final
    public BlockPos desiredPosition;

    @Inject(method = "computeHeuristic", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onComputeHeuristic(
            final int x,
            final int y,
            final int z,
            final CallbackInfoReturnable<Double> cir) {
        if (this.desiredPosition != null) {
            cir.setReturnValue(NavigationHeuristicEvaluator.adjustHeuristic(
                    cir.getReturnValueD(), this.desiredPosition, x, y, z));
        }
    }
}
