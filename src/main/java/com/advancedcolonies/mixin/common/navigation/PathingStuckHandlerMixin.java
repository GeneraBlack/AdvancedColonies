package com.advancedcolonies.mixin.common.navigation;

import com.advancedcolonies.common.debug.ServerDebugSessionManager;
import com.advancedcolonies.common.navigation.NavigationStuckAndRescueHelper;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.entity.pathfinding.IMinecoloniesNavigator;
import com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin Mixin hook on {@link PathingStuckHandler} that:
 * <ul>
 *   <li>Updates real-time 3D movement & stall tracking via {@link NavigationStuckAndRescueHelper#updateAndEvaluateStuck}.</li>
 *   <li>Applies {@code validPathStuckPatienceMultiplier} ONLY when the citizen is genuinely making spatial progress.</li>
 *   <li>Executes shallow-water ceiling rescue and multi-stage unstuck/teleport rescue when stalled.</li>
 *   <li>Honors {@code disableStuckTeleport} in {@code completeStuckAction}.</li>
 * </ul>
 */
@Mixin(value = PathingStuckHandler.class, remap = false)
public abstract class PathingStuckHandlerMixin<NAV extends PathNavigation & IMinecoloniesNavigator> {

    @Shadow
    private int globalTimeout;

    @Shadow
    private int stuckLevel;

    @Inject(method = "checkStuck", at = @At("HEAD"))
    private void advancedColonies$onCheckStuckHead(final NAV navigator, final CallbackInfo ci) {
        final Mob entity = navigator.getOurEntity();
        if (entity != null) {
            NavigationStuckAndRescueHelper.tryRescueShallowWaterUnderCeiling(entity);
        }

        // Update 3D spatial movement & stall tracking and run progressive unstuck rescue if stalled
        NavigationStuckAndRescueHelper.updateAndEvaluateStuck(navigator, this.stuckLevel, this.globalTimeout);

        // Only slow down globalTimeout when the citizen is actively moving along a valid path
        if (NavigationStuckAndRescueHelper.shouldGrantExtraPathPatience(navigator)) {
            final int adjustedIncrement = NavigationStuckAndRescueHelper.getAdjustedTimeoutIncrement(navigator);
            final int savedTicks = Math.max(0, 10 - adjustedIncrement);
            this.globalTimeout = Math.max(0, this.globalTimeout - savedTicks);
        }
    }

    @Inject(method = "checkStuck", at = @At("RETURN"))
    private void advancedColonies$onCheckStuckReturn(final NAV navigator, final CallbackInfo ci) {
        final Mob entity = navigator.getOurEntity();
        if (entity != null && entity.level() != null) {
            ServerDebugSessionManager.recordStuckMetrics(
                    entity.getId(),
                    this.stuckLevel,
                    this.globalTimeout,
                    entity.level().getGameTime());
        }
    }

    @Inject(method = "completeStuckAction", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onCompleteStuckActionHead(final NAV navigator, final CallbackInfo ci) {
        if (AdvancedColoniesConfig.PATHFINDING.disableStuckTeleport.get()) {
            ci.cancel();
        }
    }
}
