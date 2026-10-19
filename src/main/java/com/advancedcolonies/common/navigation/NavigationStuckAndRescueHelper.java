package com.advancedcolonies.common.navigation;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.pathfinding.IMinecoloniesNavigator;
import com.minecolonies.core.entity.pathfinding.PathfindingUtils;
import com.minecolonies.core.entity.pathfinding.navigation.AbstractAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathjobs.IDestinationPathJob;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides accurate spatial movement & stall tracking, multi-stage unstuck rescue
 * (door/gate assist, node snap, step-ahead teleport, safe destination teleport),
 * ladder/scaffolding climb correction, and shallow-water rescue for citizens.
 */
public final class NavigationStuckAndRescueHelper {

    /** Minimum 3D squared distance (1.2 blocks) required to count as genuine spatial progress from the anchor. */
    private static final double MIN_PROGRESS_DIST_SQR = 1.44D;

    /** Minimum vertical movement on a climbable to count as active climbing progress. */
    private static final double MIN_CLIMB_PROGRESS_Y = 0.60D;

    /** Minimum Euclidean distance to the target destination before stall tracking applies. */
    private static final double MIN_GOAL_DISTANCE = 2.25D;

    /** Stall threshold (in ticks) before Stage 1 soft unstuck (door open / node snap). */
    public static final int STAGE_1_NUDGE_TICKS = 60;

    /** Stall threshold (in ticks) before Stage 2 path-ahead step teleport. */
    public static final int STAGE_2_STEP_TP_TICKS = 120;

    /** Stall threshold (in ticks) before Stage 3 safe destination teleport. */
    public static final int STAGE_3_GOAL_TP_TICKS = 200;

    /** Per-entity real-time movement & stall telemetry map (keyed by entity runtime ID). */
    private static final Map<Integer, CitizenMovementTracker> TRACKERS = new ConcurrentHashMap<>();

    private NavigationStuckAndRescueHelper() {
        // Utility class
    }

    /**
     * Live movement and stall telemetry for a single citizen entity.
     */
    public static final class CitizenMovementTracker {
        private Vec3 anchorPos;
        private Vec3 lastPos;
        private long lastUpdateTick;
        private float moveSpeedBps;
        private int realStalledTicks;
        private int vanillaStuckLevel;
        private int vanillaGlobalTimeout;
        private int rescueStage;
        private String lastRescueAction = "NONE";
        private long lastRescueTick = Long.MIN_VALUE;
        @Nullable
        private BlockPos activeGoalPos;

        public CitizenMovementTracker(@NotNull final Vec3 initialPos, final long gameTime) {
            this.anchorPos = initialPos;
            this.lastPos = initialPos;
            this.lastUpdateTick = gameTime;
        }

        public float getMoveSpeedBps() {
            return moveSpeedBps;
        }

        public int getRealStalledTicks() {
            return realStalledTicks;
        }

        public int getVanillaStuckLevel() {
            return vanillaStuckLevel;
        }

        public int getVanillaGlobalTimeout() {
            return vanillaGlobalTimeout;
        }

        public int getRescueStage() {
            return rescueStage;
        }

        @NotNull
        public String getLastRescueAction() {
            return lastRescueAction;
        }

        @Nullable
        public BlockPos getActiveGoalPos() {
            return activeGoalPos;
        }

        public boolean isGenuinelyStuck() {
            return realStalledTicks >= STAGE_1_NUDGE_TICKS || vanillaStuckLevel > 0;
        }
    }

    /**
     * Retrieves the current movement tracker for an entity ID, or {@code null} if not tracked yet.
     */
    @Nullable
    public static CitizenMovementTracker getTracker(final int entityId) {
        return TRACKERS.get(entityId);
    }

    /**
     * Clears all cached movement trackers (e.g. when the server stops).
     */
    public static void clearTrackers() {
        TRACKERS.clear();
    }

    /**
     * Updates spatial progress tracking for the given navigator's entity and executes
     * progressive unstuck rescue actions when genuine physical stalling is detected.
     * <p>
     * Called once every 10 ticks from {@code PathingStuckHandler.checkStuck}.
     *
     * @param navigator            the citizen's path navigator
     * @param vanillaStuckLevel    current {@code stuckLevel} inside {@code PathingStuckHandler}
     * @param vanillaGlobalTimeout current {@code globalTimeout} inside {@code PathingStuckHandler}
     * @param <NAV>                navigator type
     * @return the updated {@link CitizenMovementTracker}, or {@code null} if the entity is invalid
     */
    @Nullable
    public static <NAV extends PathNavigation & IMinecoloniesNavigator> CitizenMovementTracker updateAndEvaluateStuck(
            @NotNull final NAV navigator,
            final int vanillaStuckLevel,
            final int vanillaGlobalTimeout) {

        final Mob entity = navigator.getOurEntity();
        if (entity == null || !entity.isAlive()) {
            if (entity != null) {
                TRACKERS.remove(entity.getId());
            }
            return null;
        }

        final Level level = entity.level();
        final long gameTime = level.getGameTime();
        final Vec3 currentPos = entity.position();

        final CitizenMovementTracker tracker = TRACKERS.computeIfAbsent(
                entity.getId(),
                id -> new CitizenMovementTracker(currentPos, gameTime));

        final long elapsedTicks = Math.max(1L, gameTime - tracker.lastUpdateTick);
        final double stepDist = currentPos.distanceTo(tracker.lastPos);
        tracker.moveSpeedBps = (float) ((stepDist / (double) elapsedTicks) * 20.0D);
        tracker.lastPos = currentPos;
        tracker.lastUpdateTick = gameTime;
        tracker.vanillaStuckLevel = vanillaStuckLevel;
        tracker.vanillaGlobalTimeout = vanillaGlobalTimeout;

        final BlockPos goalPos = resolveGoalPosition(navigator, entity);
        tracker.activeGoalPos = goalPos;

        final Path path = navigator.getPath();
        final PathResult<?> pathResult = navigator.getPathResult();
        final boolean hasActivePath = path != null && !path.isDone() && path.getNodeCount() > 1;
        final boolean hasActiveResult = pathResult != null
                && (pathResult.isComputing()
                || pathResult.isInProgress()
                || pathResult.failedToReachDestination());

        final double distToGoal = goalPos != null
                ? currentPos.distanceTo(Vec3.atCenterOf(goalPos))
                : (hasActivePath && path.getTarget() != null ? currentPos.distanceTo(Vec3.atCenterOf(path.getTarget())) : 0.0D);

        final boolean intendsToMove = (hasActivePath || hasActiveResult) && distToGoal > MIN_GOAL_DISTANCE;

        if (!intendsToMove) {
            tracker.anchorPos = currentPos;
            tracker.realStalledTicks = 0;
            tracker.rescueStage = 0;
            if (gameTime - tracker.lastRescueTick > 80L) {
                tracker.lastRescueAction = "NONE";
            }
            return tracker;
        }

        final double distFromAnchorSqr = currentPos.distanceToSqr(tracker.anchorPos);
        final double verticalDelta = Math.abs(currentPos.y - tracker.anchorPos.y);
        final boolean madeSpatialProgress = distFromAnchorSqr >= MIN_PROGRESS_DIST_SQR
                || (entity.onClimbable() && verticalDelta >= MIN_CLIMB_PROGRESS_Y);

        if (madeSpatialProgress) {
            tracker.anchorPos = currentPos;
            tracker.realStalledTicks = 0;
            tracker.rescueStage = 0;
            return tracker;
        }

        // Citizen intends to navigate to a goal > 2.25 blocks away, but has not progressed >= 1.2 blocks from anchorPos
        tracker.realStalledTicks += (int) Math.min(20L, elapsedTicks);

        // Execute multi-stage rescue when stalled thresholds are reached
        if (tracker.realStalledTicks >= STAGE_3_GOAL_TP_TICKS && tracker.rescueStage < 3) {
            tracker.rescueStage = 3;
            if (!AdvancedColoniesConfig.PATHFINDING.disableStuckTeleport.get()) {
                if (tryTeleportToSafeGoal(entity, navigator, goalPos)) {
                    tracker.lastRescueAction = "GOAL_TP";
                    tracker.lastRescueTick = gameTime;
                    tracker.anchorPos = entity.position();
                    tracker.realStalledTicks = 0;
                    tracker.rescueStage = 0;
                    return tracker;
                }
            }
        } else if (tracker.realStalledTicks >= STAGE_2_STEP_TP_TICKS && tracker.rescueStage < 2) {
            tracker.rescueStage = 2;
            if (!AdvancedColoniesConfig.PATHFINDING.disableStuckTeleport.get()) {
                if (tryStepTeleportAlongPathOrTowardGoal(entity, navigator, path, goalPos)) {
                    tracker.lastRescueAction = "STEP_TP";
                    tracker.lastRescueTick = gameTime;
                    tracker.anchorPos = entity.position();
                    return tracker;
                }
            }
            navigator.recalc();
        } else if (tracker.realStalledTicks >= STAGE_1_NUDGE_TICKS && tracker.rescueStage < 1) {
            tracker.rescueStage = 1;
            if (trySoftUnstuck(entity, navigator, path)) {
                tracker.lastRescueTick = gameTime;
                tracker.anchorPos = entity.position();
            }
        }

        return tracker;
    }

    /**
     * Checks whether the citizen is actively making real spatial progress along a valid path
     * and therefore deserves extra path timeout patience before {@code PathingStuckHandler} triggers.
     *
     * @param navigator the active navigator
     * @param <NAV>     navigator type
     * @return {@code true} only when the citizen has an active path AND is genuinely moving (not stalled)
     */
    public static <NAV extends PathNavigation & IMinecoloniesNavigator> boolean shouldGrantExtraPathPatience(
            @NotNull final NAV navigator) {

        final Mob entity = navigator.getOurEntity();
        if (entity == null) {
            return false;
        }

        final Path path = navigator.getPath();
        if (path == null || path.isDone() || path.getNodeCount() <= 1) {
            return false;
        }

        final CitizenMovementTracker tracker = TRACKERS.get(entity.getId());
        if (tracker != null && tracker.realStalledTicks >= 20) {
            // Citizen is not making spatial progress — do NOT grant extra patience!
            return false;
        }

        if (entity.onClimbable()) {
            return true;
        }

        return tracker == null || tracker.moveSpeedBps >= 0.45F;
    }

    /**
     * Checks whether a citizen entity is currently making spatial progress (not stalled).
     */
    public static boolean isActivelyMakingProgress(@NotNull final Mob entity) {
        final CitizenMovementTracker tracker = TRACKERS.get(entity.getId());
        if (tracker == null) {
            return false;
        }
        return tracker.realStalledTicks < 30 && tracker.moveSpeedBps >= 0.35F;
    }

    /**
     * Computes the scaled globalTimeout increment per {@code checkStuck} call (default vanilla is 10 ticks).
     * When the citizen has a valid path and is actively moving, the timeout grows more slowly according to
     * {@link com.advancedcolonies.config.PathfindingConfig#validPathStuckPatienceMultiplier}.
     *
     * @param navigator the active navigator
     * @param <NAV>     navigator type
     * @return the adjusted tick increment (between 1 and 10)
     */
    public static <NAV extends PathNavigation & IMinecoloniesNavigator> int getAdjustedTimeoutIncrement(
            @NotNull final NAV navigator) {

        if (!shouldGrantExtraPathPatience(navigator)) {
            return 10;
        }

        final double multiplier = Math.max(1.0D, AdvancedColoniesConfig.PATHFINDING.validPathStuckPatienceMultiplier.get());
        return Math.max(1, (int) Math.round(10.0D / multiplier));
    }

    /**
     * Immediately forces an unstuck rescue on the given citizen (used by {@code /acdebug unstuck}
     * and by the Builder stall watchdog).
     *
     * @param citizen           the citizen to rescue
     * @param allowGoalTeleport whether teleporting directly to the safe destination is allowed
     * @return {@code true} if a rescue action was performed
     */
    public static boolean forceUnstuckCitizen(
            @NotNull final AbstractEntityCitizen citizen,
            final boolean allowGoalTeleport) {

        final Level level = citizen.level();
        final long gameTime = level.getGameTime();
        final CitizenMovementTracker tracker = TRACKERS.computeIfAbsent(
                citizen.getId(),
                id -> new CitizenMovementTracker(citizen.position(), gameTime));

        tryOpenNearbyDoorsAndGates(citizen);
        tryRescueShallowWaterUnderCeiling(citizen);

        if (citizen.getNavigation() instanceof MinecoloniesAdvancedPathNavigate typedNav) {
            final BlockPos goalPos = resolveGoalPosition(typedNav, citizen);
            final Path path = typedNav.getPath();

            if (allowGoalTeleport && goalPos != null && tryTeleportToSafeGoal(citizen, typedNav, goalPos)) {
                tracker.lastRescueAction = "MANUAL_GOAL_TP";
                tracker.lastRescueTick = gameTime;
                tracker.anchorPos = citizen.position();
                tracker.realStalledTicks = 0;
                tracker.rescueStage = 0;
                return true;
            }

            if (tryStepTeleportAlongPathOrTowardGoal(citizen, typedNav, path, goalPos)) {
                tracker.lastRescueAction = "MANUAL_STEP_TP";
                tracker.lastRescueTick = gameTime;
                tracker.anchorPos = citizen.position();
                tracker.realStalledTicks = 0;
                tracker.rescueStage = 0;
                return true;
            }

            if (trySoftUnstuck(citizen, typedNav, path)) {
                tracker.lastRescueAction = "MANUAL_NUDGE";
                tracker.lastRescueTick = gameTime;
                tracker.anchorPos = citizen.position();
                tracker.realStalledTicks = 0;
                tracker.rescueStage = 0;
                return true;
            }
        }

        // Fallback: step to the best adjacent standable block
        final BlockPos safeNeighbor = findSafeStandNear(level, citizen.blockPosition(), 3, 2);
        if (safeNeighbor != null && !safeNeighbor.equals(citizen.blockPosition())) {
            citizen.teleportTo(safeNeighbor.getX() + 0.5D, safeNeighbor.getY(), safeNeighbor.getZ() + 0.5D);
            citizen.setDeltaMovement(Vec3.ZERO);
            citizen.getNavigation().stop();
            tracker.lastRescueAction = "MANUAL_UNJAM";
            tracker.lastRescueTick = gameTime;
            tracker.anchorPos = citizen.position();
            tracker.realStalledTicks = 0;
            tracker.rescueStage = 0;
            return true;
        }

        return false;
    }

    /**
     * Resolves the best current destination target for a navigator.
     */
    @Nullable
    public static <NAV extends PathNavigation & IMinecoloniesNavigator> BlockPos resolveGoalPosition(
            @NotNull final NAV navigator,
            @NotNull final Mob entity) {

        final BlockPos safeDest = navigator.getSafeDestination();
        if (safeDest != null && !BlockPos.ZERO.equals(safeDest)) {
            return safeDest;
        }

        final PathResult<?> result = navigator.getPathResult();
        if (result != null && result.getJob() instanceof IDestinationPathJob destJob) {
            final BlockPos jobDest = destJob.getDestination();
            if (jobDest != null && !BlockPos.ZERO.equals(jobDest)) {
                return jobDest;
            }
        }

        final Path path = navigator.getPath();
        if (path != null && path.getTarget() != null && !BlockPos.ZERO.equals(path.getTarget())) {
            return path.getTarget();
        }

        final BlockPos targetPos = navigator.getTargetPos();
        if (targetPos != null && !BlockPos.ZERO.equals(targetPos)) {
            return targetPos;
        }

        if (entity instanceof AbstractEntityCitizen citizen && citizen.getCitizenData() != null) {
            return citizen.getCitizenData().getStatusPosition();
        }

        return null;
    }

    /**
     * Stage 1 Soft Unstuck: opens nearby closed doors/fence gates, rescues from shallow water,
     * applies climbable impulse, or snaps the citizen onto the current/next path node if obstructed by a corner.
     */
    private static <NAV extends PathNavigation & IMinecoloniesNavigator> boolean trySoftUnstuck(
            @NotNull final Mob entity,
            @NotNull final NAV navigator,
            @Nullable final Path path) {

        final CitizenMovementTracker tracker = TRACKERS.get(entity.getId());
        boolean acted = false;

        if (tryOpenNearbyDoorsAndGates(entity)) {
            if (tracker != null) {
                tracker.lastRescueAction = "DOOR_GATE";
            }
            acted = true;
        }

        if (tryRescueShallowWaterUnderCeiling(entity)) {
            if (tracker != null) {
                tracker.lastRescueAction = "WATER_RESCUE";
            }
            acted = true;
        }

        final Level level = entity.level();
        if (path != null && !path.isDone() && path.getNodeCount() > 0) {
            final int nextIdx = Math.min(path.getNodeCount() - 1, path.getNextNodeIndex());
            final Node node = path.getNode(nextIdx);
            final BlockPos nodePos = new BlockPos(node.x, node.y, node.z);
            final double distSqr = entity.position().distanceToSqr(node.x + 0.5D, node.y, node.z + 0.5D);
            if (distSqr <= 9.0D && canStandAt(level, nodePos)) {
                entity.setPos(node.x + 0.5D, node.y, node.z + 0.5D);
                if (nextIdx + 1 < path.getNodeCount()) {
                    path.setNextNodeIndex(nextIdx + 1);
                }
                if (tracker != null) {
                    tracker.lastRescueAction = "NODE_SNAP";
                }
                return true;
            }
        }

        navigator.recalc();
        return acted;
    }

    /**
     * Stage 2 Step Teleport: teleports the citizen 2–5 nodes ahead along their active path,
     * or onto an unobstructed standable position closer to the goal.
     */
    private static <NAV extends PathNavigation & IMinecoloniesNavigator> boolean tryStepTeleportAlongPathOrTowardGoal(
            @NotNull final Mob entity,
            @NotNull final NAV navigator,
            @Nullable final Path path,
            @Nullable final BlockPos goalPos) {

        final Level level = entity.level();

        if (path != null && !path.isDone() && path.getNodeCount() > 1) {
            final int startIdx = Math.max(0, path.getNextNodeIndex());
            final int maxLookAhead = Math.min(path.getNodeCount() - 1, startIdx + 6);
            for (int idx = maxLookAhead; idx > startIdx; idx--) {
                final Node node = path.getNode(idx);
                final BlockPos candidate = new BlockPos(node.x, node.y, node.z);
                if (!candidate.equals(entity.blockPosition()) && canStandAt(level, candidate)) {
                    entity.stopRiding();
                    entity.teleportTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                    entity.setDeltaMovement(Vec3.ZERO);
                    path.setNextNodeIndex(Math.min(path.getNodeCount() - 1, idx + 1));
                    return true;
                }
            }
        }

        // Fallback when path is null/done or no path node ahead is standable: step toward goalPos
        final BlockPos origin = entity.blockPosition();
        BlockPos bestCandidate = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = -2; dy <= 2; dy++) {
                    final BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!canStandAt(level, candidate)) {
                        continue;
                    }
                    final double score = goalPos != null
                            ? candidate.distSqr(goalPos)
                            : - origin.distSqr(candidate);
                    if (score < bestScore) {
                        bestScore = score;
                        bestCandidate = candidate;
                    }
                }
            }
        }

        if (bestCandidate != null) {
            entity.stopRiding();
            entity.teleportTo(bestCandidate.getX() + 0.5D, bestCandidate.getY(), bestCandidate.getZ() + 0.5D);
            entity.setDeltaMovement(Vec3.ZERO);
            navigator.recalc();
            return true;
        }

        return false;
    }

    /**
     * Stage 3 Goal Teleport: teleports the citizen to a verified safe standable block at or near {@code goalPos}.
     */
    public static <NAV extends PathNavigation & IMinecoloniesNavigator> boolean tryTeleportToSafeGoal(
            @NotNull final Mob entity,
            @Nullable final NAV navigator,
            @Nullable final BlockPos goalPos) {

        if (goalPos == null || BlockPos.ZERO.equals(goalPos)) {
            return false;
        }

        final Level level = entity.level();
        if (!level.isLoaded(goalPos)) {
            return false;
        }

        final BlockPos safeTarget = findSafeStandNear(level, goalPos, 5, 3);
        if (safeTarget == null) {
            return false;
        }

        entity.stopRiding();
        entity.teleportTo(safeTarget.getX() + 0.5D, safeTarget.getY(), safeTarget.getZ() + 0.5D);
        entity.setDeltaMovement(Vec3.ZERO);
        if (navigator != null) {
            navigator.stop();
        }
        return true;
    }

    /**
     * Searches around {@code center} within {@code horizRadius} and {@code vertRadius} for a safe
     * 2-block-tall standable position (solid floor, unobstructed head/feet, no lava/fire/berry bushes).
     */
    @Nullable
    public static BlockPos findSafeStandNear(
            @NotNull final Level level,
            @NotNull final BlockPos center,
            final int horizRadius,
            final int vertRadius) {

        if (canStandAt(level, center)) {
            return center;
        }

        for (int r = 1; r <= horizRadius; r++) {
            for (int dy = 0; dy <= vertRadius; dy++) {
                final int[] ySteps = dy == 0 ? new int[]{0} : new int[]{-dy, dy};
                for (final int yOff : ySteps) {
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                                continue;
                            }
                            final BlockPos candidate = center.offset(dx, yOff, dz);
                            if (canStandAt(level, candidate)) {
                                return candidate;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Validates that an entity can safely stand at {@code pos} (solid support below,
     * passable feet & head, no lava, fire, or sweet berry bushes).
     */
    public static boolean canStandAt(@NotNull final Level level, @NotNull final BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        final BlockPos belowPos = pos.below();
        final BlockState floor = level.getBlockState(belowPos);
        final BlockState feet = level.getBlockState(pos);
        final BlockState head = level.getBlockState(pos.above());

        if (!floor.blocksMotion() || PathfindingUtils.isLava(level, belowPos, floor, floor.getFluidState())) {
            return false;
        }
        if (feet.blocksMotion() && !(feet.getBlock() instanceof DoorBlock) && !(feet.getBlock() instanceof FenceGateBlock)) {
            return false;
        }
        if (head.blocksMotion() && !(head.getBlock() instanceof DoorBlock)) {
            return false;
        }
        if (!feet.getFluidState().isEmpty() || !head.getFluidState().isEmpty()) {
            return false;
        }
        return !(feet.getBlock() instanceof SweetBerryBushBlock) && !(head.getBlock() instanceof SweetBerryBushBlock);
    }

    /**
     * Opens any closed wooden door or fence gate directly at or adjacent to the citizen's position.
     */
    public static boolean tryOpenNearbyDoorsAndGates(@NotNull final Mob entity) {
        final Level level = entity.level();
        final BlockPos origin = entity.blockPosition();
        boolean openedAny = false;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    final BlockPos pos = origin.offset(dx, dy, dz);
                    final BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                        level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 10);
                        openedAny = true;
                    } else if (state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand() && !state.getValue(DoorBlock.OPEN)) {
                        door.setOpen(entity, level, state, pos, true);
                        openedAny = true;
                    }
                }
            }
        }
        return openedAny;
    }

    /**
     * Corrects the vanilla MineColonies West-ladder Y-offset bug (where {@code case WEST:} in
     * {@code MinecoloniesAdvancedPathNavigate} accidentally added {@code +0.8D} to {@code newY}
     * instead of {@code newX}) and applies a gentle upward climb impulse when ascending
     * ladders, vines, or scaffolding.
     */
    public static double correctClimbWantedYAndImpulse(
            @NotNull final Mob entity,
            @NotNull final Direction ladderFacing,
            final double currentWantedY) {

        double correctedY = currentWantedY;
        if (AdvancedColoniesConfig.PATHFINDING.fixLadderAndScaffoldingClimb.get()) {
            if (ladderFacing == Direction.WEST) {
                correctedY -= 0.8D;
            }

            final BlockPos pos = entity.blockPosition();
            final BlockState feetState = entity.level().getBlockState(pos);
            final boolean isClimbBlock = feetState.getBlock() instanceof LadderBlock
                    || feetState.getBlock() instanceof VineBlock
                    || feetState.getBlock() instanceof ScaffoldingBlock
                    || entity.onClimbable();

            if (isClimbBlock && correctedY > entity.getY() + 0.1D) {
                final Vec3 motion = entity.getDeltaMovement();
                if (motion.y < 0.16D) {
                    entity.setDeltaMovement(motion.x * 0.6D, 0.18D, motion.z * 0.6D);
                }
            }
        }
        return correctedY;
    }

    /**
     * Centers a citizen on the rail exit node when dismounting a MineColonies minecart
     * so they are not ejected into adjacent walls, fences, or off bridge edges.
     */
    public static void centerEntityOnRailExit(
            @NotNull final Mob entity,
            final int nodeX,
            final int nodeY,
            final int nodeZ) {

        if (!AdvancedColoniesConfig.PATHFINDING.centerMinecartDismount.get()) {
            return;
        }

        final double targetX = nodeX + 0.5D;
        final double targetY = nodeY + 0.1D;
        final double targetZ = nodeZ + 0.5D;

        if (entity.distanceToSqr(targetX, targetY, targetZ) <= 9.0D) {
            entity.setPos(targetX, targetY, targetZ);
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * Rescues a citizen trapped in a 1-block water pocket directly underneath a solid ceiling
     * by nudging them horizontally toward the nearest adjacent block with 2-block headroom.
     */
    public static boolean tryRescueShallowWaterUnderCeiling(@NotNull final Mob entity) {
        if (!AdvancedColoniesConfig.PATHFINDING.enableShallowWaterRescue.get()) {
            return false;
        }

        if (!entity.isInWater()) {
            return false;
        }

        final Level level = entity.level();
        final BlockPos feetPos = entity.blockPosition();
        final BlockPos headPos = feetPos.above();
        final BlockState headState = level.getBlockState(headPos);

        if (!headState.blocksMotion() && headState.getFluidState().isEmpty()) {
            return false;
        }

        for (final Direction dir : Direction.Plane.HORIZONTAL) {
            final BlockPos neighborFeet = feetPos.relative(dir);
            final BlockPos neighborHead = neighborFeet.above();
            final BlockState nFeetState = level.getBlockState(neighborFeet);
            final BlockState nHeadState = level.getBlockState(neighborHead);

            if (!nFeetState.blocksMotion()
                    && !nHeadState.blocksMotion()
                    && !nHeadState.getFluidState().is(FluidTags.WATER)
                    && !nHeadState.getFluidState().is(FluidTags.LAVA)) {

                final Vec3 push = Vec3.atLowerCornerOf(dir.getNormal()).scale(0.14D);
                final Vec3 cur = entity.getDeltaMovement();
                entity.setDeltaMovement(cur.x + push.x, Math.min(cur.y, 0.02D), cur.z + push.z);
                return true;
            }
        }
        return false;
    }
}
