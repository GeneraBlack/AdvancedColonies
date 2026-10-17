package com.advancedcolonies.workers.builder;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.CitizenConstants.INITIAL_RUN_SPEED_AVOID;

/**
 * Tracks lava hazards around a Builder's active worksite, manages active retreat movement,
 * and reroutes unsafe standing positions to nearby lava-free coordinates.
 */
public final class BuilderLavaHazardTracker {

    /** Regular tick interval between lava scans near the worksite. */
    private static final int LAVA_CHECK_INTERVAL = 15;

    /** Faster tick interval shortly after a lava threat was encountered. */
    private static final int LAVA_CHECK_INTERVAL_WHEN_THREATENED = 5;

    /** Tick window during which the builder uses the faster re-check interval. */
    private static final int LAVA_RECENT_THREAT_WINDOW = 40;

    /** Standard retry delay (in ticks) after completing a lava retreat. */
    private static final int LAVA_RETRY_DELAY = 15;

    /** Extended retry delay (in ticks) when the same hazard repeatedly blocks the same target. */
    private static final int LAVA_PERSISTENT_RETRY_DELAY = 100;

    /** Number of consecutive detections before treating a hazard as persistent. */
    private static final int LAVA_PERSISTENT_THRESHOLD = 3;

    /** Tick window in which repeated detections accumulate toward the persistent threshold. */
    private static final int LAVA_PERSISTENT_WINDOW = 80;

    /** Maximum 2D distance within which lava scans are activated when approaching the target. */
    private static final int LAVA_ACTIVATION_DISTANCE = 12;

    /** Exclusion radius around a recently detected hazard for the same work target. */
    private static final int LAVA_BLOCKED_STAND_DISTANCE = 6;

    /** Search radius for finding an alternative lava-free stand position. */
    private static final int SAFE_STAND_SEARCH_RADIUS = 6;

    @Nullable
    private BlockPos activeHazardRetreatPos = null;
    private long lastLavaCheckTick = -LAVA_CHECK_INTERVAL;
    private long lastLavaThreatTick = -LAVA_RECENT_THREAT_WINDOW;

    @Nullable
    private BlockPos lastLavaCheckTarget = null;
    @Nullable
    private BlockPos lastLavaCheckStandPos = null;

    @Nullable
    private BlockPos lastHazardPos = null;
    @Nullable
    private BlockPos lastHazardTarget = null;
    private long lastHazardTick = Long.MIN_VALUE;
    private int repeatedHazardDetections = 0;
    private long hazardRetryBlockedUntilTick = Long.MIN_VALUE;

    /**
     * Resets all tracked lava hazard state (called when changing structure or completing work orders).
     */
    public void reset() {
        activeHazardRetreatPos = null;
        lastLavaCheckTick = -LAVA_CHECK_INTERVAL;
        lastLavaThreatTick = -LAVA_RECENT_THREAT_WINDOW;
        lastLavaCheckTarget = null;
        lastLavaCheckStandPos = null;
        lastHazardPos = null;
        lastHazardTarget = null;
        lastHazardTick = Long.MIN_VALUE;
        repeatedHazardDetections = 0;
        hazardRetryBlockedUntilTick = Long.MIN_VALUE;
    }

    /**
     * Continues an active retreat away from a detected lava hazard.
     *
     * @param worker the builder citizen entity.
     * @return {@code true} if the builder is currently retreating (or just finished retreating).
     */
    public boolean tickActiveRetreat(@NotNull final AbstractEntityCitizen worker) {
        if (activeHazardRetreatPos == null) {
            return false;
        }

        final int retreatDist = AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.lavaRetreatDistance.get();
        if (BlockPosUtil.getDistance2D(worker.blockPosition(), activeHazardRetreatPos) >= retreatDist) {
            activeHazardRetreatPos = null;
            worker.getNavigation().stop();
            return true;
        }

        if (!EntityNavigationUtils.walkAwayFrom(worker, activeHazardRetreatPos, retreatDist, INITIAL_RUN_SPEED_AVOID)) {
            return true;
        }

        activeHazardRetreatPos = null;
        return true;
    }

    /**
     * Checks whether retrying {@code currentBlock} is temporarily blocked due to a persistent lava hazard.
     *
     * @param world        the world level.
     * @param currentBlock the active construction target block.
     * @return remaining backoff ticks if blocked, or {@code 0} if not blocked.
     */
    public int getRemainingBackoffTicks(@NotNull final Level world, @NotNull final BlockPos currentBlock) {
        if (lastHazardTarget == null || !currentBlock.equals(lastHazardTarget)) {
            return 0;
        }
        final long remaining = hazardRetryBlockedUntilTick - world.getGameTime();
        return remaining > 0 ? (int) Math.min(Integer.MAX_VALUE, remaining) : 0;
    }

    /**
     * Returns the retry delay to apply after a hazard retreat or stand rejection.
     *
     * @param world the world level.
     * @return delay in ticks.
     */
    public int getRetryDelay(@NotNull final Level world) {
        final long remaining = hazardRetryBlockedUntilTick - world.getGameTime();
        return remaining > 0 ? (int) Math.min(Integer.MAX_VALUE, remaining) : LAVA_RETRY_DELAY;
    }

    /**
     * Scans for dangerous lava near the builder's current position when approaching {@code currentBlock}.
     *
     * @param world        the world level.
     * @param worker       the builder entity.
     * @param currentBlock the current target block.
     * @param workFrom     the current planned working stand, or {@code null}.
     * @return the lava position if dangerous lava is found, or {@code null}.
     */
    @Nullable
    public BlockPos checkCurrentStandForLava(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen worker,
            @NotNull final BlockPos currentBlock,
            @Nullable final BlockPos workFrom) {

        if (!AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.enableBuilderLavaHazardRetreat.get()) {
            return null;
        }

        if (!shouldCheckForLava(world, worker, currentBlock, workFrom)) {
            return null;
        }

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return BuilderStandSafetyEvaluator.findLavaNearStand(world, cursor, worker.blockPosition());
    }

    /**
     * Initiates a retreat from {@code hazardPos} for target {@code currentBlock}.
     *
     * @param world        the world level.
     * @param worker       the builder entity.
     * @param hazardPos    the detected lava position.
     * @param currentBlock the target block being worked on.
     */
    public void beginRetreat(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen worker,
            @NotNull final BlockPos hazardPos,
            @NotNull final BlockPos currentBlock) {

        lastLavaThreatTick = world.getGameTime();
        activeHazardRetreatPos = new BlockPos(hazardPos.getX(), worker.blockPosition().getY(), hazardPos.getZ());
        recordHazardDetection(world, activeHazardRetreatPos, currentBlock);
        worker.getNavigation().stop();
    }

    /**
     * Validates a preferred working stand against lava hazards, rerouting to a nearby safe stand if necessary.
     *
     * @param world          the world level.
     * @param worker         the builder entity.
     * @param currentBlock   the target block being worked on.
     * @param preferredStand the candidate stand position.
     * @return a verified safe stand position, or {@code null} if no safe stand is available.
     */
    @Nullable
    public BlockPos resolveSafeWorkingStand(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen worker,
            @NotNull final BlockPos currentBlock,
            @Nullable final BlockPos preferredStand) {

        if (preferredStand == null) {
            return null;
        }

        if (!AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.enableBuilderLavaHazardRetreat.get()) {
            return preferredStand;
        }

        final BlockPos safeStand = findSafeWorkingStand(world, worker, currentBlock, preferredStand);
        if (safeStand != null) {
            return safeStand;
        }

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final BlockPos lavaPos = BuilderStandSafetyEvaluator.findLavaNearStand(world, cursor, preferredStand);
        if (lavaPos != null) {
            lastLavaThreatTick = world.getGameTime();
            recordHazardDetection(world, lavaPos, currentBlock);
        }
        return null;
    }

    /**
     * Checks whether a candidate stand lies inside the exclusion zone of a recently detected lava hazard.
     */
    public boolean isBlockedByRecentHazard(@NotNull final BlockPos currentBlock, @NotNull final BlockPos stand) {
        return lastHazardPos != null
                && lastHazardTarget != null
                && currentBlock.equals(lastHazardTarget)
                && BlockPosUtil.getDistance2D(stand, lastHazardPos) <= LAVA_BLOCKED_STAND_DISTANCE;
    }

    private boolean shouldCheckForLava(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen worker,
            @NotNull final BlockPos currentBlock,
            @Nullable final BlockPos workFrom) {

        if (workFrom == null && BlockPosUtil.getDistance2D(worker.blockPosition(), currentBlock) > LAVA_ACTIVATION_DISTANCE) {
            return false;
        }

        final BlockPos standPos = worker.blockPosition();
        final long gameTime = world.getGameTime();
        final int interval = (gameTime - lastLavaThreatTick <= LAVA_RECENT_THREAT_WINDOW)
                ? LAVA_CHECK_INTERVAL_WHEN_THREATENED
                : LAVA_CHECK_INTERVAL;

        if (currentBlock.equals(lastLavaCheckTarget)
                && standPos.equals(lastLavaCheckStandPos)
                && gameTime - lastLavaCheckTick < interval) {
            return false;
        }

        lastLavaCheckTick = gameTime;
        lastLavaCheckTarget = currentBlock.immutable();
        lastLavaCheckStandPos = standPos.immutable();
        return true;
    }

    private void recordHazardDetection(
            @NotNull final Level world,
            @NotNull final BlockPos hazardPos,
            @NotNull final BlockPos currentBlock) {

        final long gameTime = world.getGameTime();
        final boolean sameHazard = lastHazardPos != null
                && BlockPosUtil.getDistance2D(hazardPos, lastHazardPos) <= LAVA_BLOCKED_STAND_DISTANCE
                && currentBlock.equals(lastHazardTarget)
                && gameTime - lastHazardTick <= LAVA_PERSISTENT_WINDOW;

        repeatedHazardDetections = sameHazard ? repeatedHazardDetections + 1 : 1;
        lastHazardPos = hazardPos.immutable();
        lastHazardTarget = currentBlock.immutable();
        lastHazardTick = gameTime;
        hazardRetryBlockedUntilTick = gameTime + (repeatedHazardDetections >= LAVA_PERSISTENT_THRESHOLD
                ? LAVA_PERSISTENT_RETRY_DELAY
                : LAVA_RETRY_DELAY);
    }

    @Nullable
    private BlockPos findSafeWorkingStand(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen worker,
            @NotNull final BlockPos currentBlock,
            @NotNull final BlockPos preferredStand) {

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final BlockPos currentStand = worker.blockPosition();
        if (isUsableSafeStand(world, currentBlock, currentStand, cursor)) {
            return currentStand.immutable();
        }

        if (isUsableSafeStand(world, currentBlock, preferredStand, cursor)) {
            return preferredStand;
        }

        for (int radius = 1; radius <= SAFE_STAND_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    final BlockPos candidate = preferredStand.offset(dx, 0, dz);
                    if (isUsableSafeStand(world, currentBlock, candidate, cursor)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isUsableSafeStand(
            @NotNull final Level world,
            @NotNull final BlockPos currentBlock,
            @NotNull final BlockPos stand,
            @NotNull final BlockPos.MutableBlockPos cursor) {

        if (BlockPosUtil.getDistance2D(stand, currentBlock) > 5) {
            return false;
        }
        if (stand.getX() == currentBlock.getX() && stand.getZ() == currentBlock.getZ() && stand.getY() >= currentBlock.getY()) {
            return false;
        }
        if (isBlockedByRecentHazard(currentBlock, stand)) {
            return false;
        }
        return BuilderStandSafetyEvaluator.canUseStandPosition(world, stand)
                && BuilderStandSafetyEvaluator.findLavaNearStand(world, cursor, stand) == null;
    }
}
