package com.advancedcolonies.mixin.common.navigation;

import com.advancedcolonies.common.navigation.NavigationStuckAndRescueHelper;
import com.minecolonies.api.util.Vec3Mutable;
import com.minecolonies.core.entity.pathfinding.PathPointExtended;
import com.minecolonies.core.entity.pathfinding.navigation.AbstractAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link MinecoloniesAdvancedPathNavigate} that fixes:
 * <ul>
 *   <li>The vanilla MineColonies {@code case WEST:} ladder Y-offset bug ({@code +0.8D} Y) and scaffolding climbing impulse.</li>
 *   <li>Centered dismounting when exiting a {@code MinecoloniesMinecart} at the end of a rail path segment.</li>
 * </ul>
 */
@Mixin(value = MinecoloniesAdvancedPathNavigate.class, remap = false)
public abstract class MinecoloniesAdvancedPathNavigateMixin extends AbstractAdvancedPathNavigate {

    @Shadow
    private Vec3Mutable wantedPosition;

    protected MinecoloniesAdvancedPathNavigateMixin(@NotNull final Mob entity, final Level world) {
        super(entity, world);
    }

    @Shadow(remap = false)
    private PathPointExtended currentNode() {
        return null;
    }

    @Inject(method = "handleLadders", at = @At("RETURN"), remap = false)
    private void advancedColonies$onHandlePathPointOnLadderReturn(
            final CallbackInfoReturnable<Boolean> cir) {

        final PathPointExtended pEx = this.currentNode();
        if (this.ourEntity == null || pEx == null || this.wantedPosition == null || this.wantedPosition.empty()) {
            return;
        }

        final Direction facing = pEx.getLadderFacing();
        if (facing != null && facing != Direction.DOWN && !Boolean.TRUE.equals(cir.getReturnValue())) {
            final double correctedY = NavigationStuckAndRescueHelper.correctClimbWantedYAndImpulse(
                    this.ourEntity,
                    facing,
                    this.wantedPosition.getY());
            if (correctedY != this.wantedPosition.getY()) {
                this.wantedPosition.set(this.wantedPosition.getX(), correctedY, this.wantedPosition.getZ());
                this.ourEntity.getMoveControl().setWantedPosition(
                        this.wantedPosition.getX(),
                        correctedY,
                        this.wantedPosition.getZ(),
                        0.3D);
            }
        }
    }

    @Inject(
            method = "followThePath",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;stopRiding()V",
                    shift = At.Shift.AFTER,
                    remap = true
            )
    )
    private void advancedColonies$onAfterRailDismount(final CallbackInfo ci) {
        if (this.ourEntity == null || this.path == null || this.path.isDone()) {
            return;
        }
        final int curNode = this.path.getNextNodeIndex();
        if (curNode >= 0 && curNode < this.path.getNodeCount()) {
            final Node node = this.path.getNode(curNode);
            NavigationStuckAndRescueHelper.centerEntityOnRailExit(this.ourEntity, node.x, node.y, node.z);
        }
    }

    @Inject(method = "isStuck", at = @At("RETURN"), cancellable = true, remap = false)
    private void advancedColonies$onIsStuck(final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && this.ourEntity != null) {
            final NavigationStuckAndRescueHelper.CitizenMovementTracker tracker =
                    NavigationStuckAndRescueHelper.getTracker(this.ourEntity.getId());
            if (tracker != null && tracker.getRealStalledTicks() >= NavigationStuckAndRescueHelper.STAGE_2_STEP_TP_TICKS) {
                cir.setReturnValue(true);
            }
        }
    }
}
