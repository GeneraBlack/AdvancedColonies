package com.advancedcolonies.mixin.common.navigation;

import com.advancedcolonies.common.navigation.NavigationCostEvaluator;
import com.minecolonies.core.entity.pathfinding.MNode;
import com.minecolonies.core.entity.pathfinding.PathingOptions;
import com.minecolonies.core.entity.pathfinding.pathjobs.AbstractPathJob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link AbstractPathJob} delegating main-thread building footprint caching,
 * deterministic pathing options, and step cost evaluation to {@link NavigationCostEvaluator}.
 */
@Mixin(value = AbstractPathJob.class, remap = false)
public abstract class AbstractPathJobMixin {

    @Shadow
    @Final
    private Level actualWorld;

    @Shadow
    private PathingOptions pathingOptions;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void advancedColonies$onConstructedOnMainThread(final CallbackInfo ci) {
        NavigationCostEvaluator.prepareFootprintsOnMainThread(this.actualWorld);
    }

    @Inject(method = "search", at = @At("HEAD"))
    private void advancedColonies$onSearchStart(final CallbackInfoReturnable<Path> cir) {
        if (this.pathingOptions != null) {
            NavigationCostEvaluator.optimizePathingOptions(this.pathingOptions);
        }
    }

    @Inject(method = "computeCost", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onComputeCost(
            final MNode parent,
            final int dX,
            final int dY,
            final int dZ,
            final boolean isSwimming,
            final boolean onPath,
            final boolean isDiving,
            final boolean onRails,
            final boolean railsExit,
            final boolean swimStart,
            final boolean ladder,
            final BlockState state,
            final BlockState below,
            final int x,
            final int y,
            final int z,
            final CallbackInfoReturnable<Double> cir) {

        final double adjusted = NavigationCostEvaluator.adjustStepCost(
                cir.getReturnValueD(),
                this.pathingOptions,
                this.actualWorld,
                parent,
                dY,
                isSwimming,
                onPath,
                isDiving,
                onRails,
                swimStart,
                ladder,
                state,
                below,
                x,
                y,
                z
        );
        cir.setReturnValue(adjusted);
    }
}
