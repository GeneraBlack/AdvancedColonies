package com.advancedcolonies.workers.herder;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.AgricultureAndHerdingConfig;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Comprehensive server-side AI controller and survival enhancer for Minecraft {@link Bee} entities
 * and MineColonies Beekeeper apiaries:
 * <ul>
 *   <li><b>Persistent Hive Memory & Home Tether</b>: Prevents bees from forgetting or blacklisting their
 *       home hive and keeps them within {@code beeMaxHiveWanderRadius} (inside MineColonies' 22-block
 *       Beekeeper scan range).</li>
 *   <li><b>Smart Hive Approach, Deadzone Fix & 3D Unstuck</b>: Guides returning bees directly to the
 *       front face of their beehive, eliminates the 2.0–2.6 block entry deadzone, and rescues bees
 *       trapped under roofs, leaves, fences, or corners.</li>
 *   <li><b>Hazard Avoidance & Beekeeper Safety</b>: Protects bees from campfires, water drowning,
 *       berry bushes, and cacti, and prevents bees from stinging MineColonies citizens to death.</li>
 *   <li><b>Fast Flower Discovery & Pollination</b>: Scans up to {@code beeFlowerSearchRadius} blocks
 *       for safe flowers, removes vanilla's 2400-tick known-flower delay, and completes pollination
 *       within {@code beePollinationDurationTicks}.</li>
 * </ul>
 */
public final class BeeAIHelper {

    public static final String TAG_PENNED = "AdvancedColonies_Penned";
    public static final String TAG_BOUND_HIVE = "AC_BoundHive";
    private static final String TAG_STUCK_TICKS = "AC_BeeStuckTicks";
    private static final String TAG_RETURN_TICKS = "AC_BeeReturnTicks";
    private static final String TAG_POLLINATE_TICKS = "AC_BeePollinateTicks";
    private static final String TAG_LAST_X = "AC_LastX";
    private static final String TAG_LAST_Y = "AC_LastY";
    private static final String TAG_LAST_Z = "AC_LastZ";

    private static final Direction[] HORIZONTAL_DIRS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private BeeAIHelper() {
    }

    /**
     * Checks whether the enhanced Bee AI module is enabled in config.
     */
    public static boolean isEnhancedBeeAIEnabled() {
        return AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.enableEnhancedBeeAI.get();
    }

    /**
     * Checks whether hazard and sting-death protection is enabled for bees.
     */
    public static boolean isHazardProtectionEnabled() {
        return isEnhancedBeeAIEnabled()
                && AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.beeProtectFromHazardsAndStingDeath.get();
    }

    /**
     * Evaluates whether incoming damage to a {@link Bee} should be cancelled (campfires, fire,
     * drowning, sweet berry bushes, cacti) and applies an immediate upward escape impulse.
     */
    public static boolean shouldCancelBeeDamage(@NotNull final Bee bee, @NotNull final DamageSource source) {
        if (!isHazardProtectionEnabled()) {
            return false;
        }

        if (source.is(DamageTypes.CAMPFIRE)
                || source.is(DamageTypes.IN_FIRE)
                || source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypes.HOT_FLOOR)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || source.is(DamageTypes.CACTUS)
                || source.is(DamageTypes.IN_WALL)) {
            bee.clearFire();
            final Vec3 dv = bee.getDeltaMovement();
            bee.setDeltaMovement(dv.x * 0.8D, Math.max(0.18D, dv.y + 0.14D), dv.z * 0.8D);
            return true;
        }
        return false;
    }

    /**
     * Prevents bees from stinging MineColonies citizens (such as the Beekeeper) and dying.
     */
    public static boolean shouldPreventBeeSting(@NotNull final Bee bee, @Nullable final Entity target) {
        if (!isHazardProtectionEnabled()) {
            return false;
        }
        if (target instanceof AbstractEntityCitizen) {
            bee.stopBeingAngry();
            return true;
        }
        return false;
    }

    /**
     * Binds bees discovered by a MineColonies Beekeeper to the Apiary's registered beehives
     * so they stay anchored to the Beekeeper's work area.
     */
    public static void bindColonyBeesToHives(
            @Nullable final Level level,
            @Nullable final Collection<BlockPos> beekeeperHives,
            @Nullable final List<Bee> bees) {

        if (level == null || beekeeperHives == null || beekeeperHives.isEmpty() || bees == null || bees.isEmpty()) {
            return;
        }

        for (final Bee bee : bees) {
            if (bee == null || !bee.isAlive()) {
                continue;
            }
            final CompoundTag tag = bee.getPersistentData();
            tag.putBoolean(TAG_PENNED, true);

            final BlockPos currentHive = bee.getHivePos();
            if (currentHive != null && beekeeperHives.contains(currentHive) && isValidHiveBlock(level, currentHive)) {
                tag.putLong(TAG_BOUND_HIVE, currentHive.asLong());
                continue;
            }

            // Assign homeless or non-colony bee to the nearest Beekeeper hive with available space
            BlockPos bestHive = null;
            double bestDistSqr = Double.MAX_VALUE;
            for (final BlockPos candidate : beekeeperHives) {
                if (!isValidHiveBlock(level, candidate)) {
                    continue;
                }
                final BlockEntity be = level.getBlockEntity(candidate);
                final boolean hasSpace = !(be instanceof BeehiveBlockEntity beehive) || !beehive.isFull();
                final double distSqr = bee.blockPosition().distSqr(candidate);
                if (hasSpace && distSqr < bestDistSqr) {
                    bestDistSqr = distSqr;
                    bestHive = candidate;
                }
            }
            if (bestHive == null) {
                bestHive = beekeeperHives.iterator().next();
            }
            if (bestHive != null && isValidHiveBlock(level, bestHive)) {
                bee.setHivePos(bestHive.immutable());
                tag.putLong(TAG_BOUND_HIVE, bestHive.asLong());
                bee.getBlacklistedHives().clear();
            }
        }
    }

    /**
     * Checks whether {@code hivePos} is a valid Beehive or Bee Nest (treating unloaded chunks as valid
     * so bees never lose their hive reference when a chunk temporarily unloads).
     */
    public static boolean isValidHiveBlock(@NotNull final Level level, @Nullable final BlockPos hivePos) {
        if (hivePos == null) {
            return false;
        }
        if (!level.isLoaded(hivePos)) {
            return true;
        }
        return level.getBlockState(hivePos).is(BlockTags.BEEHIVES)
                || level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity;
    }

    /**
     * Smarter replacement for {@code Bee#pathfindRandomlyTowards(BlockPos)} that flies on a clean,
     * hazard-free vector toward {@code targetPos} instead of wandering into water or walls.
     */
    public static boolean smartPathfindTowards(@NotNull final Bee bee, @NotNull final BlockPos targetPos) {
        if (!isEnhancedBeeAIEnabled()) {
            return false;
        }

        final Level level = bee.level();
        final Vec3 targetCenter = findSafeApproachPos(level, targetPos);
        final Vec3 beePos = bee.position();
        final Vec3 delta = targetCenter.subtract(beePos);
        final double dist = delta.length();

        if (dist <= 0.001D) {
            return true;
        }

        final double stepDist = Math.min(dist, 10.0D);
        final Vec3 dir = delta.scale(1.0D / dist);
        Vec3 stepTarget = beePos.add(dir.scale(stepDist));

        final BlockPos stepBlock = BlockPos.containing(stepTarget);
        if (!isSafeAirForBee(level, stepBlock)) {
            for (int dy = 1; dy <= 3; dy++) {
                if (isSafeAirForBee(level, stepBlock.above(dy))) {
                    stepTarget = new Vec3(stepTarget.x, stepBlock.getY() + dy + 0.5D, stepTarget.z);
                    break;
                }
            }
        }

        bee.getNavigation().setMaxVisitedNodesMultiplier(4.0F);
        final boolean pathStarted = bee.getNavigation().moveTo(stepTarget.x, stepTarget.y, stepTarget.z, 1.15D);
        if (!pathStarted || dist <= 6.0D) {
            bee.getMoveControl().setWantedPosition(stepTarget.x, stepTarget.y, stepTarget.z, 1.1D);
        }
        return true;
    }

    /**
     * Main server-side AI tick invoked from {@code BeeMixin#customServerAiStep}.
     */
    public static void tickEnhancedBeeAI(
            @NotNull final Bee bee,
            final boolean wantsToEnterHive,
            final int ticksWithoutNectar) {

        if (!isEnhancedBeeAIEnabled() || !(bee.level() instanceof ServerLevel level)) {
            return;
        }

        final AgricultureAndHerdingConfig cfg = AdvancedColoniesConfig.AGRICULTURE_AND_HERDING;
        final CompoundTag tag = bee.getPersistentData();

        // 1. Hazard escape & colony citizen anger suppression
        if (cfg.beeProtectFromHazardsAndStingDeath.get()) {
            escapeHazards(bee, level);
            if (bee.getTarget() instanceof AbstractEntityCitizen
                    || bee.getLastHurtByMob() instanceof AbstractEntityCitizen) {
                bee.stopBeingAngry();
            }
        }

        // 2. Persistent Hive Memory (prevent vanilla from dropping or blacklisting valid hives)
        maintainHiveBinding(bee, level, tag);

        final BlockPos hivePos = bee.getHivePos();
        final int maxWanderRadius = Math.max(6, Math.min(32, cfg.beeMaxHiveWanderRadius.get()));

        // 3. Home Radius Leash (keep bees within maxWanderRadius of their hive so they never drift away)
        if (hivePos != null && level.isLoaded(hivePos)) {
            final Vec3 hiveCenter = Vec3.atCenterOf(hivePos);
            final double distSqr = bee.position().distanceToSqr(hiveCenter);
            final double maxRadiusSqr = (double) maxWanderRadius * maxWanderRadius;
            final double hardTeleportSqr = (double) (maxWanderRadius + 10) * (maxWanderRadius + 10);

            if (distSqr > hardTeleportSqr) {
                final Vec3 safeEntrance = findSafeApproachPos(level, hivePos);
                bee.moveTo(safeEntrance.x, safeEntrance.y, safeEntrance.z, bee.getYRot(), bee.getXRot());
                bee.getNavigation().stop();
                return;
            } else if (distSqr > maxRadiusSqr && !bee.isAngry()) {
                final Vec3 safeEntrance = findSafeApproachPos(level, hivePos);
                steerBeeTowards(bee, safeEntrance, 1.2D);
            }
        }

        // 4. Smart Hive Return, Deadzone Fix & 3D Unstuck
        if (wantsToEnterHive && hivePos != null && level.isLoaded(hivePos)) {
            handleSmartHiveReturn(bee, level, hivePos, tag);
            return;
        } else {
            tag.putInt(TAG_RETURN_TICKS, 0);
        }

        // 5. Fast Flower Discovery & Accelerated Pollination (when looking for nectar)
        if (!bee.hasNectar() && !level.isRaining() && !bee.isAngry()) {
            handleFlowerSearchAndPollination(bee, level, hivePos, maxWanderRadius, ticksWithoutNectar, cfg, tag);
        } else {
            tag.putInt(TAG_POLLINATE_TICKS, 0);
        }

        // 6. Enhanced Crop Fertilization while carrying nectar back to the hive
        if (bee.hasNectar() && bee.tickCount % 20 == 0) {
            tryFertilizeCropsBelow(bee, level);
        }
    }

    /**
     * Propels the bee upward out of water, lava, campfires, fire, and berry bushes.
     */
    private static void escapeHazards(@NotNull final Bee bee, @NotNull final ServerLevel level) {
        final BlockPos pos = bee.blockPosition();
        final BlockState atState = level.getBlockState(pos);
        final BlockState belowState = level.getBlockState(pos.below());

        final boolean inFluid = bee.isInWaterOrBubble() || bee.isInLava() || !level.getFluidState(pos).isEmpty();
        final boolean overHazard = isHazardBlock(atState) || isHazardBlock(belowState);

        if (inFluid || overHazard) {
            bee.clearFire();
            final Vec3 dv = bee.getDeltaMovement();
            bee.setDeltaMovement(dv.x * 0.85D, Math.max(0.20D, dv.y + 0.12D), dv.z * 0.85D);
            final BlockPos safeAbove = pos.above(2);
            if (isSafeAirForBee(level, safeAbove)) {
                bee.getMoveControl().setWantedPosition(
                        safeAbove.getX() + 0.5D, safeAbove.getY() + 0.5D, safeAbove.getZ() + 0.5D, 1.25D);
            }
        }
    }

    /**
     * Preserves and restores the bee's home hive binding so temporary pathing hiccups never make it homeless.
     */
    private static void maintainHiveBinding(
            @NotNull final Bee bee,
            @NotNull final ServerLevel level,
            @NotNull final CompoundTag tag) {

        final BlockPos currentHive = bee.getHivePos();
        if (currentHive != null) {
            if (isValidHiveBlock(level, currentHive)) {
                tag.putLong(TAG_BOUND_HIVE, currentHive.asLong());
                if (!bee.getBlacklistedHives().isEmpty()) {
                    bee.getBlacklistedHives().clear();
                }
                return;
            }
            bee.setHivePos(null);
        }

        if (tag.contains(TAG_BOUND_HIVE)) {
            final BlockPos savedHive = BlockPos.of(tag.getLong(TAG_BOUND_HIVE));
            if (isValidHiveBlock(level, savedHive)) {
                if (!level.isLoaded(savedHive)) {
                    bee.setHivePos(savedHive);
                    return;
                }
                final BlockEntity be = level.getBlockEntity(savedHive);
                if (be instanceof BeehiveBlockEntity beehive
                        && (!beehive.isFull() || tag.getBoolean(TAG_PENNED))) {
                    bee.setHivePos(savedHive);
                    bee.getBlacklistedHives().clear();
                    return;
                }
            } else {
                tag.remove(TAG_BOUND_HIVE);
            }
        }
    }

    /**
     * Guides a bee returning to its hive directly to the entrance face, fixes the 2.0..2.6 block
     * entry deadzone, and rescues bees stuck under roofs, leaves, or corners.
     */
    private static void handleSmartHiveReturn(
            @NotNull final Bee bee,
            @NotNull final ServerLevel level,
            @NotNull final BlockPos hivePos,
            @NotNull final CompoundTag tag) {

        bee.getBlacklistedHives().clear();

        final BlockEntity be = level.getBlockEntity(hivePos);
        if (!(be instanceof BeehiveBlockEntity beehive)) {
            return;
        }

        if (beehive.isFull()) {
            // If this bee's bound hive is temporarily full (3 bees inside), try to find an adjacent non-full hive
            if (bee.tickCount % 40 == 0) {
                final BlockPos alternateHive = findNearbyHiveWithSpace(level, hivePos, 12);
                if (alternateHive != null) {
                    bee.setHivePos(alternateHive);
                    tag.putLong(TAG_BOUND_HIVE, alternateHive.asLong());
                }
            }
            return;
        }

        final Vec3 entrancePos = findSafeApproachPos(level, hivePos);
        final Vec3 hiveCenter = Vec3.atCenterOf(hivePos);
        final double distToHiveSqr = bee.position().distanceToSqr(hiveCenter);
        final double distToEntranceSqr = bee.position().distanceToSqr(entrancePos);

        // Direct Entry Fix: enter immediately when within 2.65 blocks of hive center or 1.75 blocks of entrance
        if (distToHiveSqr <= 7.0D || distToEntranceSqr <= 3.06D) {
            tag.putInt(TAG_STUCK_TICKS, 0);
            tag.putInt(TAG_RETURN_TICKS, 0);
            beehive.addOccupant(bee);
            return;
        }

        // Within 4.5 blocks: glide directly to the front entrance air block so the bee doesn't get stuck on the back of the hive
        if (distToHiveSqr <= 20.25D) {
            bee.getMoveControl().setWantedPosition(entrancePos.x, entrancePos.y, entrancePos.z, 1.15D);
        }

        final int returnTicks = tag.getInt(TAG_RETURN_TICKS) + 1;
        tag.putInt(TAG_RETURN_TICKS, returnTicks);

        // Check movement progress every 10 ticks for 3D unstuck assistance
        if (bee.tickCount % 10 == 0) {
            final double lastX = tag.getDouble(TAG_LAST_X);
            final double lastY = tag.getDouble(TAG_LAST_Y);
            final double lastZ = tag.getDouble(TAG_LAST_Z);
            final double movedSqr = bee.position().distanceToSqr(lastX, lastY, lastZ);

            tag.putDouble(TAG_LAST_X, bee.getX());
            tag.putDouble(TAG_LAST_Y, bee.getY());
            tag.putDouble(TAG_LAST_Z, bee.getZ());

            int stuckTicks = tag.getInt(TAG_STUCK_TICKS);
            if (movedSqr < 0.08D) {
                stuckTicks += 10;
            } else {
                stuckTicks = Math.max(0, stuckTicks - 5);
            }
            tag.putInt(TAG_STUCK_TICKS, stuckTicks);

            if (stuckTicks >= 30) {
                // Nudge away from ceiling/wall obstacles toward the open entrance
                applyUnstuckNudge(bee, level, entrancePos);
            }

            if (stuckTicks >= 100 || returnTicks >= 300) {
                // Guaranteed unstuck entry: place bee at entrance and insert into hive
                tag.putInt(TAG_STUCK_TICKS, 0);
                tag.putInt(TAG_RETURN_TICKS, 0);
                bee.moveTo(entrancePos.x, entrancePos.y, entrancePos.z, bee.getYRot(), bee.getXRot());
                beehive.addOccupant(bee);
            }
        }
    }

    /**
     * Expands flower discovery to {@code beeFlowerSearchRadius}, removes the 2400-tick known-flower wait,
     * and completes pollination after {@code beePollinationDurationTicks}.
     */
    private static void handleFlowerSearchAndPollination(
            @NotNull final Bee bee,
            @NotNull final ServerLevel level,
            @Nullable final BlockPos hivePos,
            final int maxWanderRadius,
            final int ticksWithoutNectar,
            @NotNull final AgricultureAndHerdingConfig cfg,
            @NotNull final CompoundTag tag) {

        BlockPos flowerPos = bee.getSavedFlowerPos();
        if (flowerPos != null) {
            final boolean withinHiveRange = hivePos == null
                    || flowerPos.distSqr(hivePos) <= (double) (maxWanderRadius + 2) * (maxWanderRadius + 2);
            if (!withinHiveRange || !isValidSafeFlower(level, flowerPos)) {
                bee.setSavedFlowerPos(null);
                flowerPos = null;
            }
        }

        // Scan for a nearby flower if the bee doesn't have one yet
        if (flowerPos == null && bee.tickCount % 25 == 0) {
            final int searchRadius = Math.max(5, Math.min(32, cfg.beeFlowerSearchRadius.get()));
            final BlockPos searchOrigin = (hivePos != null && level.isLoaded(hivePos))
                    ? hivePos
                    : bee.blockPosition();
            final int effectiveRadius = (hivePos != null)
                    ? Math.min(searchRadius, maxWanderRadius)
                    : searchRadius;
            flowerPos = findBestFlowerInRadius(level, searchOrigin, bee.blockPosition(), effectiveRadius);
            if (flowerPos != null) {
                bee.setSavedFlowerPos(flowerPos);
            }
        }

        if (flowerPos == null) {
            tag.putInt(TAG_POLLINATE_TICKS, 0);
            return;
        }

        final Vec3 hoverTarget = Vec3.atBottomCenterOf(flowerPos).add(0.0D, 0.6D, 0.0D);
        final double distToFlowerSqr = bee.position().distanceToSqr(hoverTarget);
        final boolean pollinateGoalRunning = isPriorityGoalRunning(bee, 4);

        if (distToFlowerSqr <= 2.56D || pollinateGoalRunning) {
            // Actively hovering over flower: increment accelerated pollination timer
            final int pollinateTicks = tag.getInt(TAG_POLLINATE_TICKS) + 1;
            tag.putInt(TAG_POLLINATE_TICKS, pollinateTicks);

            if (!pollinateGoalRunning) {
                bee.getMoveControl().setWantedPosition(hoverTarget.x, hoverTarget.y, hoverTarget.z, 0.35D);
                bee.getLookControl().setLookAt(hoverTarget.x, hoverTarget.y - 0.3D, hoverTarget.z);
                if (pollinateTicks % 55 == 0) {
                    bee.playSound(SoundEvents.BEE_POLLINATE, 0.8F, 1.0F);
                }
            }

            final int requiredTicks = Math.max(40, Math.min(400, cfg.beePollinationDurationTicks.get()));
            if (pollinateTicks >= requiredTicks) {
                tag.putInt(TAG_POLLINATE_TICKS, 0);
                stopPriorityGoal(bee, 4);
                bee.resetTicksWithoutNectarSinceExitingHive();
                setBeeHasNectar(bee, true);
                bee.playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
            }
        } else if (ticksWithoutNectar >= 30) {
            // Guide the bee directly toward its flower after just 1.5s instead of waiting 2400 ticks (2 minutes)!
            if (bee.tickCount % 15 == 0 || !bee.getNavigation().isInProgress()) {
                steerBeeTowards(bee, hoverTarget, 1.15D);
            }
        }
    }

    /**
     * Fertilizes {@code BlockTags.BEE_GROWABLES} crops up to 4 blocks below a nectar-carrying bee.
     */
    private static void tryFertilizeCropsBelow(@NotNull final Bee bee, @NotNull final ServerLevel level) {
        if (level.random.nextFloat() > 0.35F) {
            return;
        }

        final BlockPos beeBlock = bee.blockPosition();
        for (int dy = 1; dy <= 4; dy++) {
            final BlockPos checkPos = beeBlock.below(dy);
            final BlockState state = level.getBlockState(checkPos);
            if (!state.is(BlockTags.BEE_GROWABLES)) {
                continue;
            }

            final Block block = state.getBlock();
            BlockState grownState = null;
            if (block instanceof CropBlock crop && !crop.isMaxAge(state)) {
                grownState = crop.getStateForAge(crop.getAge(state) + 1);
            } else if (block instanceof StemBlock && state.getValue(StemBlock.AGE) < 7) {
                grownState = state.setValue(StemBlock.AGE, state.getValue(StemBlock.AGE) + 1);
            } else if (state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) < 3) {
                grownState = state.setValue(SweetBerryBushBlock.AGE, state.getValue(SweetBerryBushBlock.AGE) + 1);
            } else if ((state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT))
                    && block instanceof BonemealableBlock bonemealable
                    && bonemealable.isValidBonemealTarget(level, checkPos, state)) {
                bonemealable.performBonemeal(level, level.random, checkPos, state);
                level.levelEvent(2011, checkPos, 15);
                return;
            }

            if (grownState != null) {
                level.levelEvent(2011, checkPos, 15);
                level.setBlockAndUpdate(checkPos, grownState);
                return;
            }
        }
    }

    /**
     * Finds the optimal safe air coordinate directly in front of a beehive (or above a flower/target).
     */
    @NotNull
    public static Vec3 findSafeApproachPos(@NotNull final Level level, @NotNull final BlockPos targetPos) {
        if (!level.isLoaded(targetPos)) {
            return Vec3.atCenterOf(targetPos);
        }

        final BlockState state = level.getBlockState(targetPos);
        if (state.hasProperty(BeehiveBlock.FACING)) {
            final Direction facing = state.getValue(BeehiveBlock.FACING);
            final BlockPos frontPos = targetPos.relative(facing);
            if (isSafeAirForBee(level, frontPos)) {
                return Vec3.atCenterOf(frontPos);
            }
        }

        for (final Direction dir : HORIZONTAL_DIRS) {
            final BlockPos adj = targetPos.relative(dir);
            if (isSafeAirForBee(level, adj)) {
                return Vec3.atCenterOf(adj);
            }
        }

        if (isSafeAirForBee(level, targetPos.above())) {
            return Vec3.atCenterOf(targetPos.above());
        }
        return Vec3.atCenterOf(targetPos);
    }

    /**
     * Applies a 3D clearance nudge when a bee is stuck under a ceiling, tree leaves, or fence corner.
     */
    private static void applyUnstuckNudge(
            @NotNull final Bee bee,
            @NotNull final ServerLevel level,
            @NotNull final Vec3 target) {

        final BlockPos pos = bee.blockPosition();
        final boolean blockedAbove = !level.getBlockState(pos.above()).isAir();
        final double dy = blockedAbove ? -0.16D : (target.y > bee.getY() + 0.5D ? 0.18D : 0.10D);

        final Vec3 toTarget = target.subtract(bee.position());
        final double horizLen = Math.hypot(toTarget.x, toTarget.z);
        final double dx = horizLen > 0.01D ? (toTarget.x / horizLen) * 0.18D : 0.0D;
        final double dz = horizLen > 0.01D ? (toTarget.z / horizLen) * 0.18D : 0.0D;

        bee.setDeltaMovement(dx, dy, dz);
        bee.getNavigation().stop();
        bee.getMoveControl().setWantedPosition(target.x, target.y, target.z, 1.2D);
    }

    private static void steerBeeTowards(@NotNull final Bee bee, @NotNull final Vec3 target, final double speed) {
        final Path currentPath = bee.getNavigation().getPath();
        if (currentPath == null || currentPath.isDone() || !currentPath.canReach()) {
            bee.getNavigation().setMaxVisitedNodesMultiplier(4.0F);
            final boolean started = bee.getNavigation().moveTo(target.x, target.y, target.z, speed);
            if (!started) {
                bee.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);
            }
        } else if (bee.position().distanceToSqr(target) <= 25.0D) {
            bee.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);
        }
    }

    @Nullable
    private static BlockPos findNearbyHiveWithSpace(
            @NotNull final ServerLevel level,
            @NotNull final BlockPos origin,
            final int radius) {

        BlockPos best = null;
        double bestDistSqr = Double.MAX_VALUE;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.setWithOffset(origin, dx, dy, dz);
                    if (!level.isLoaded(cursor) || !level.getBlockState(cursor).is(BlockTags.BEEHIVES)) {
                        continue;
                    }
                    final BlockEntity be = level.getBlockEntity(cursor);
                    if (be instanceof BeehiveBlockEntity beehive && !beehive.isFull()) {
                        final double dSqr = origin.distSqr(cursor);
                        if (dSqr < bestDistSqr) {
                            bestDistSqr = dSqr;
                            best = cursor.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findBestFlowerInRadius(
            @NotNull final ServerLevel level,
            @NotNull final BlockPos searchCenter,
            @NotNull final BlockPos beePos,
            final int radius) {

        final List<BlockPos> candidates = new ArrayList<>();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) {
                        continue;
                    }
                    cursor.setWithOffset(searchCenter, dx, dy, dz);
                    if (isValidSafeFlower(level, cursor)) {
                        candidates.add(cursor.immutable());
                        if (candidates.size() >= 16) {
                            break;
                        }
                    }
                }
                if (candidates.size() >= 16) {
                    break;
                }
            }
            if (candidates.size() >= 16) {
                break;
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort((a, b) -> Double.compare(a.distSqr(beePos), b.distSqr(beePos)));
        final int pickBound = Math.min(4, candidates.size());
        return candidates.get(level.random.nextInt(pickBound));
    }

    private static boolean isValidSafeFlower(@NotNull final Level level, @NotNull final BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        final BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            return false;
        }
        if (!state.is(BlockTags.FLOWERS)) {
            return false;
        }
        if (state.is(Blocks.SUNFLOWER) && state.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.UPPER) {
            return false;
        }
        if (state.is(Blocks.WITHER_ROSE)) {
            return false;
        }
        return isSafeAirForBee(level, pos.above());
    }

    private static boolean isSafeAirForBee(@NotNull final Level level, @NotNull final BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        final BlockState state = level.getBlockState(pos);
        if (!state.getCollisionShape(level, pos).isEmpty() && !state.is(BlockTags.FLOWERS)) {
            return false;
        }
        if (!level.getFluidState(pos).isEmpty() || isHazardBlock(state)) {
            return false;
        }
        final BlockState below = level.getBlockState(pos.below());
        return !isHazardBlock(below) && level.getFluidState(pos.below()).isEmpty();
    }

    private static boolean isHazardBlock(@NotNull final BlockState state) {
        return state.getBlock() instanceof CampfireBlock
                || state.is(BlockTags.FIRE)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.WITHER_ROSE);
    }

    private static boolean isPriorityGoalRunning(@NotNull final Bee bee, final int priority) {
        for (final WrappedGoal wrapped : bee.getGoalSelector().getAvailableGoals()) {
            if (wrapped.getPriority() == priority && wrapped.isRunning()) {
                return true;
            }
        }
        return false;
    }

    private static void stopPriorityGoal(@NotNull final Bee bee, final int priority) {
        for (final WrappedGoal wrapped : bee.getGoalSelector().getAvailableGoals()) {
            if (wrapped.getPriority() == priority && wrapped.isRunning()) {
                wrapped.stop();
            }
        }
    }

    private static void setBeeHasNectar(@NotNull final Bee bee, final boolean hasNectar) {
        final CompoundTag tag = new CompoundTag();
        bee.addAdditionalSaveData(tag);
        tag.putBoolean(Bee.TAG_HAS_NECTAR, hasNectar);
        if (hasNectar) {
            tag.putInt(Bee.TAG_TICKS_SINCE_POLLINATION, 0);
        }
        bee.readAdditionalSaveData(tag);
    }
}
