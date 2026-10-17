package com.advancedcolonies.common.navigation;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.PathfindingConfig;
import com.minecolonies.api.entity.pathfinding.IMinecoloniesNavigator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Provides gentle unstuck patience and physical rescue helpers for navigating citizens:
 * <ul>
 *   <li>Grants citizens extra time when following a valid, active path (or climbing a ladder)
 *       so they do not prematurely teleport while walking along longer roads or stairs.</li>
 *   <li>Optionally suppresses stuck-teleportation entirely if configured.</li>
 *   <li>Rescues citizens trapped in 1-block shallow water pockets under solid ceilings.</li>
 *   <li>Stabilizes horizontal motion on ladders to prevent diagonal slip-offs.</li>
 * </ul>
 */
public final class NavigationStuckAndRescueHelper {

    private NavigationStuckAndRescueHelper() {
        // Utility class
    }

    /**
     * Checks whether the entity is currently following a valid, active path or climbing,
     * warranting extra patience before triggering unstuck actions.
     *
     * @param navigator the citizen's navigator.
     * @return {@code true} if the citizen is actively on a valid path or ladder.
     */
    public static <NAV extends PathNavigation & IMinecoloniesNavigator> boolean shouldGrantExtraPathPatience(
            @NotNull final NAV navigator) {

        final Mob entity = navigator.getOurEntity();
        if (entity == null) {
            return false;
        }

        if (entity.onClimbable()) {
            return true;
        }

        final Path path = navigator.getPath();
        return path != null && !path.isDone() && path.getNodeCount() > 1;
    }

    /**
     * Computes the per-check timeout increment (vanilla default is {@code 10}).
     * When the citizen is on a valid path, reduces the increment according to
     * {@link PathfindingConfig#validPathStuckPatienceMultiplier}.
     *
     * @param navigator the citizen's navigator.
     * @return adjusted timeout increment (>= 1).
     */
    public static <NAV extends PathNavigation & IMinecoloniesNavigator> int getAdjustedTimeoutIncrement(
            @NotNull final NAV navigator) {

        if (!shouldGrantExtraPathPatience(navigator)) {
            return 10;
        }
        final double mult = AdvancedColoniesConfig.PATHFINDING.validPathStuckPatienceMultiplier.get();
        return Math.max(1, (int) Math.round(10.0D / Math.max(1.0D, mult)));
    }

    /**
     * Checks whether emergency stuck-teleportation should be suppressed.
     */
    public static boolean isStuckTeleportDisabled() {
        return AdvancedColoniesConfig.PATHFINDING.disableStuckTeleport.get();
    }

    /**
     * Performs per-check physical rescue for citizens stuck in shallow water under a ceiling
     * or slipping diagonally off a ladder.
     *
     * @param navigator the citizen's navigator.
     */
    public static <NAV extends PathNavigation & IMinecoloniesNavigator> void tickPhysicalRescue(
            @NotNull final NAV navigator) {

        final Mob entity = navigator.getOurEntity();
        if (entity == null) {
            return;
        }

        final PathfindingConfig cfg = AdvancedColoniesConfig.PATHFINDING;
        if (cfg.enableLadderSlipProtection.get() && entity.onClimbable()) {
            stabilizeOnLadder(entity);
        }

        if (cfg.enableShallowWaterRescue.get() && entity.isInWater()) {
            tryRescueFromCeilingWaterPocket(entity);
        }
    }

    /**
     * Dampens excessive lateral velocity while climbing so citizens stay centered on ladders.
     */
    private static void stabilizeOnLadder(@NotNull final Mob entity) {
        final Vec3 delta = entity.getDeltaMovement();
        if (Math.abs(delta.x) > 0.12D || Math.abs(delta.z) > 0.12D) {
            entity.setDeltaMovement(
                    Math.clamp(delta.x, -0.10D, 0.10D),
                    Math.max(delta.y, -0.15D),
                    Math.clamp(delta.z, -0.10D, 0.10D)
            );
        }
    }

    /**
     * If a citizen is in water with a solid ceiling directly overhead and losing air,
     * nudges them onto an adjacent breathable walkable block.
     */
    private static void tryRescueFromCeilingWaterPocket(@NotNull final Mob entity) {
        if (entity.getAirSupply() > entity.getMaxAirSupply() - 40) {
            return;
        }

        final Level world = entity.level();
        final BlockPos pos = entity.blockPosition();
        final BlockState headAbove = world.getBlockState(pos.above(2));
        if (!headAbove.blocksMotion() && headAbove.getFluidState().isEmpty()) {
            return;
        }

        for (final Direction dir : Direction.Plane.HORIZONTAL) {
            final BlockPos candidate = pos.relative(dir);
            final BlockState floor = world.getBlockState(candidate.below());
            final BlockState feet = world.getBlockState(candidate);
            final BlockState head = world.getBlockState(candidate.above());
            if (floor.blocksMotion()
                    && !feet.blocksMotion()
                    && !head.blocksMotion()
                    && !head.getFluidState().is(FluidTags.WATER)) {
                entity.teleportTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                entity.setAirSupply(entity.getMaxAirSupply());
                return;
            }
        }
    }
}
