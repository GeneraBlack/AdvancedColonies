package com.advancedcolonies.mixin.common.navigation;

import com.advancedcolonies.common.navigation.NavigationHeuristicEvaluator;
import com.minecolonies.core.entity.pathfinding.MNode;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveToLocation;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link PathJobMoveToLocation} delegating near-target 3D heuristic
 * and vertical dead-end scoring to {@link NavigationHeuristicEvaluator}.
 */
@Mixin(value = PathJobMoveToLocation.class, remap = false)
public abstract class PathJobMoveToLocationMixin {

    @Shadow
    @Final
    @NotNull
    protected BlockPos destination;

    @Inject(method = "computeHeuristic", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onComputeHeuristic(
            final int x,
            final int y,
            final int z,
            final CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(NavigationHeuristicEvaluator.adjustHeuristic(
                cir.getReturnValueD(), this.destination, x, y, z));
    }

    @Inject(method = "getEndNodeScore", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onGetEndNodeScore(
            @NotNull final MNode node,
            final CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(NavigationHeuristicEvaluator.adjustEndNodeScore(
                cir.getReturnValueD(), this.destination, node));
    }
}
