package com.advancedcolonies.mixin.common.navigation;

import com.advancedcolonies.common.navigation.NavigationStuckAndRescueHelper;
import com.minecolonies.api.entity.pathfinding.IMinecoloniesNavigator;
import com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin Mixin hook on {@link PathingStuckHandler} delegating valid-path patience,
 * ladder stabilization, shallow water rescue, and teleport suppression to
 * {@link NavigationStuckAndRescueHelper}.
 */
@Mixin(value = PathingStuckHandler.class, remap = false)
public abstract class PathingStuckHandlerMixin<NAV extends PathNavigation & IMinecoloniesNavigator> {

    @Shadow
    private int stuckLevel;

    @Shadow
    private int globalTimeout;

    @Shadow
    private int delayToNextUnstuckAction;

    @Shadow
    private boolean canTeleportGoal;

    @Inject(method = "checkStuck", at = @At("HEAD"))
    private void advancedColonies$onCheckStuckHead(final NAV navigator, final CallbackInfo ci) {
        NavigationStuckAndRescueHelper.tickPhysicalRescue(navigator);
        if (NavigationStuckAndRescueHelper.shouldGrantExtraPathPatience(navigator)) {
            final int adjustedIncrement = NavigationStuckAndRescueHelper.getAdjustedTimeoutIncrement(navigator);
            final int savedTicks = Math.max(0, 10 - adjustedIncrement);
            this.globalTimeout = Math.max(0, this.globalTimeout - savedTicks);
            this.delayToNextUnstuckAction += savedTicks;
        }
        if (navigator.getOurEntity() != null && navigator.getOurEntity().level() != null) {
            com.advancedcolonies.common.debug.ServerDebugSessionManager.recordStuckMetrics(
                    navigator.getOurEntity().getId(),
                    this.stuckLevel,
                    this.globalTimeout,
                    navigator.getOurEntity().level().getGameTime());
        }
    }

    @Inject(method = "completeStuckAction", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onCompleteStuckActionHead(final NAV navigator, final CallbackInfo ci) {
        if (NavigationStuckAndRescueHelper.isStuckTeleportDisabled()) {
            this.canTeleportGoal = false;
        }
    }
}
