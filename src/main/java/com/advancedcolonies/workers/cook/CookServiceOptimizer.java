package com.advancedcolonies.workers.cook;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.jobs.JobCook;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.minecolonies.api.util.constant.CitizenConstants.AVERAGE_SATURATION;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.RESTAURANT_MENU;

/**
 * Optimizes the Restaurant / Dining Hall Cook & Waiter workflow ({@code EntityAIWorkCook})
 * by transforming the vanilla FIFO queue into a <b>non-blocking, out-of-order customer pool</b>:
 * <ul>
 *   <li><b>Out-of-Order Pool & Opportunistic Drive-By Service:</b> Any waiting citizen who is already
 *       within immediate serving reach ({@code <= 3.5} blocks on the same floor) is prioritized
 *       immediately so the cook feeds nearby guests on the fly without waiting on distant targets.</li>
 *   <li><b>Stuck / Unreachable Customer Bypass (No Head-of-Line Blocking):</b> Tracks consecutive
 *       walk attempts per target citizen. If a customer cannot be reached within {@link #MAX_WALK_ATTEMPTS_BEFORE_BYPASS}
 *       cycles (e.g. stuck in a chair/corner or pathfinding fails), that citizen receives a temporary
 *       deprioritization cooldown and moves to the back of the pool so all other reachable citizens
 *       are served out-of-order first.</li>
 *   <li><b>Non-Blocking Inventory Service:</b> Ensures that if the cook already carries food for
 *       waiting guests, they are added to the pool and served before a single guest's missing rack item
 *       blocks the entire dining hall.</li>
 *   <li><b>Smart Inventory Dumping:</b> Prevents the cook from dumping their inventory after every single
 *       action ({@code getActionsDoneUntilDumping() == 1} in vanilla) while hungry customers are waiting.</li>
 * </ul>
 */
public final class CookServiceOptimizer {

    /** Actions allowed before dumping when customers are actively waiting in the restaurant. */
    private static final int ACTIVE_SERVICE_ACTIONS_UNTIL_DUMP = 64;

    /** Default actions allowed before dumping when no customers are waiting. */
    private static final int IDLE_SERVICE_ACTIONS_UNTIL_DUMP = 16;

    /**
     * Maximum consecutive {@code serveFoodToCitizen} cycles (30 ticks = 1.5s each) without distance progress
     * before a target customer is considered temporarily stuck/unreachable and bypassed out-of-order.
     */
    private static final int MAX_WALK_ATTEMPTS_BEFORE_BYPASS = 3;

    /**
     * Maximum attempts if even deprioritized/stuck customers are the only ones left in the pool,
     * after which they are dropped so the cook is never deadlocked.
     */
    private static final int MAX_WALK_ATTEMPTS_BEFORE_DROP = 6;

    /** Ticks a bypassed stuck customer stays deprioritized in the pool (300 ticks = 15 seconds). */
    private static final long UNREACHABLE_COOLDOWN_TICKS = 300L;

    /** Squared horizontal/3D distance within which a customer can be served immediately (drive-by). */
    private static final double IMMEDIATE_SERVE_RANGE_SQR = 3.5D * 3.5D;

    /** Sorting penalty added to temporarily unreachable/stuck customers so reachable guests always come first. */
    private static final double DEPRIORITIZED_SORT_PENALTY = 1_000_000.0D;

    /** Per-cook tracking state for out-of-order pool management and stuck-customer bypass. */
    private static final Map<Integer, CookPoolTracker> COOK_TRACKERS = new ConcurrentHashMap<>();

    private CookServiceOptimizer() {
        // Utility class
    }

    /**
     * Called at the return of {@code EntityAIWorkCook#checkForImportantJobs()}.
     * Populates the customer pool with all citizens who can be fed from the cook's current inventory
     * (preventing early loop termination on a single citizen from starving others), deduplicates/sorts
     * the pool out-of-order, and prioritizes immediate service when ready customers are waiting.
     *
     * @param world          the world level.
     * @param cook           the cook worker entity.
     * @param building       the restaurant building.
     * @param citizenToServe the cook's customer pool.
     * @param playerToServe  the cook's player queue.
     * @param originalState  the state returned by vanilla {@code checkForImportantJobs()}.
     * @return the optimized next {@link IAIState}.
     */
    @NotNull
    public static IAIState onCheckForImportantJobsReturn(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen cook,
            @NotNull final BuildingCook building,
            @NotNull final Queue<AbstractEntityCitizen> citizenToServe,
            @NotNull final Queue<Player> playerToServe,
            @NotNull final IAIState originalState) {

        sanitizePlayerQueue(playerToServe);

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedRestaurantCookAI.get()) {
            return originalState;
        }

        // Collect all hungry citizens in the dining hall for whom the cook ALREADY carries valid food,
        // even if vanilla aborted its scan early with GATHERING_REQUIRED_MATERIALS for an earlier citizen.
        collectReadyCustomersIntoPool(world, cook, building, citizenToServe);

        // Deduplicate, remove invalid/dropped entries, and sort pool (reachable & drive-by first, stuck last)
        refreshCitizenPoolOutOfOrder(world, cook, building, citizenToServe, false);

        if (!citizenToServe.isEmpty() && originalState == AIWorkerState.GATHERING_REQUIRED_MATERIALS) {
            final CookPoolTracker tracker = getTracker(cook.getId());
            final long now = world.getGameTime();
            for (final AbstractEntityCitizen candidate : citizenToServe) {
                if (!tracker.isDeprioritized(candidate.getId(), now)) {
                    // We have at least one reachable customer whom we can feed right now out-of-order!
                    return AIWorkerState.COOK_SERVE_FOOD_TO_CITIZEN;
                }
            }
        }

        return originalState;
    }

    /**
     * Called at the head of {@code EntityAIWorkCook#serveFoodToCitizen()}.
     * Evaluates progress toward the current target, bypasses stuck/unreachable customers out-of-order,
     * and promotes any customer within immediate drive-by reach to the front of the pool.
     *
     * @param world          the world level.
     * @param cook           the cook worker entity.
     * @param building       the restaurant building.
     * @param citizenToServe the cook's customer pool.
     */
    public static void onServeFoodToCitizenHead(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen cook,
            @NotNull final BuildingCook building,
            @NotNull final Queue<AbstractEntityCitizen> citizenToServe) {

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedRestaurantCookAI.get()) {
            return;
        }

        // Also include any newly arrived hungry citizen in the dining hall whom the cook can feed from inventory
        collectReadyCustomersIntoPool(world, cook, building, citizenToServe);

        if (citizenToServe.isEmpty()) {
            return;
        }

        refreshCitizenPoolOutOfOrder(world, cook, building, citizenToServe, true);
    }

    /**
     * Scans the building for hungry citizens whose required menu food is already in the cook's inventory
     * and ensures they are present in {@code citizenToServe}.
     */
    private static void collectReadyCustomersIntoPool(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen cook,
            @NotNull final BuildingCook building,
            @NotNull final Queue<AbstractEntityCitizen> citizenToServe) {

        final RestaurantMenuModule module = building.getModule(RESTAURANT_MENU);
        if (module == null) {
            return;
        }

        final CookPoolTracker tracker = getTracker(cook.getId());
        final long now = world.getGameTime();

        for (final EntityCitizen citizen : WorldUtil.getEntitiesWithinBuilding(world, EntityCitizen.class, building, null)) {
            if (citizen.getCitizenJobHandler().getColonyJob() instanceof JobCook
                    || !isCitizenStillHungry(citizen)
                    || hasValidMenuFoodInInventory(citizen, building, module)) {
                continue;
            }
            if (tracker.isDropped(citizen.getId(), now)) {
                continue;
            }
            if (FoodUtils.hasBestOptionInInv(cook.getInventoryCitizen(), citizen.getCitizenData(), module.getMenu(), building)) {
                citizenToServe.add(citizen);
            }
        }
    }

    /**
     * Deduplicates {@code citizenToServe}, tracks whether the current head customer is stuck/unreachable,
     * and orders the pool so reachable/drive-by customers are served out-of-order without waiting on
     * blocked citizens.
     */
    private static void refreshCitizenPoolOutOfOrder(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen cook,
            @NotNull final BuildingCook building,
            @NotNull final Queue<AbstractEntityCitizen> citizenToServe,
            final boolean isServeTick) {

        if (citizenToServe.isEmpty()) {
            return;
        }

        final long now = world.getGameTime();
        final CookPoolTracker tracker = getTracker(cook.getId());
        tracker.cleanupExpired(now);

        final RestaurantMenuModule module = building.getModule(RESTAURANT_MENU);

        // 1. Deduplicate and filter valid hungry citizens still inside the building
        final Set<Integer> seenIds = new LinkedHashSet<>();
        final List<AbstractEntityCitizen> pool = new ArrayList<>(citizenToServe.size());
        for (final AbstractEntityCitizen candidate : citizenToServe) {
            if (candidate == null || !candidate.isAlive()) {
                continue;
            }
            if (!building.isInBuilding(candidate.blockPosition()) || !isCitizenStillHungry(candidate)) {
                tracker.clearCustomer(candidate.getId());
                continue;
            }
            if (module != null && hasValidMenuFoodInInventory(candidate, building, module)) {
                tracker.clearCustomer(candidate.getId());
                continue;
            }
            if (tracker.isDropped(candidate.getId(), now)) {
                continue;
            }
            if (seenIds.add(candidate.getId())) {
                pool.add(candidate);
            }
        }

        if (pool.isEmpty()) {
            citizenToServe.clear();
            tracker.resetActiveTarget();
            return;
        }

        final BlockPos cookPos = cook.blockPosition();

        // 2. Evaluate progress on the current active target during serveFoodToCitizen ticks
        if (isServeTick) {
            final AbstractEntityCitizen currentHead = pool.get(0);
            final int headId = currentHead.getId();
            final double distSqr = cookPos.distSqr(currentHead.blockPosition());

            if (distSqr > IMMEDIATE_SERVE_RANGE_SQR) {
                final int stalledCycles = tracker.recordWalkAttempt(headId, distSqr);
                if (stalledCycles >= MAX_WALK_ATTEMPTS_BEFORE_DROP) {
                    // Customer has been completely unreachable even after bypass cycles; drop temporarily
                    // so the cook is never deadlocked and can resume cooking/stocking.
                    tracker.markDropped(headId, now + UNREACHABLE_COOLDOWN_TICKS);
                    cook.getNavigation().stop();
                    pool.remove(0);
                    tracker.resetActiveTarget();
                } else if (stalledCycles >= MAX_WALK_ATTEMPTS_BEFORE_BYPASS) {
                    // Bypass this stuck customer out-of-order so other guests are served immediately!
                    tracker.markDeprioritized(headId, now + UNREACHABLE_COOLDOWN_TICKS);
                    cook.getNavigation().stop();
                    tracker.resetActiveTarget();
                }
            } else {
                // Within serving distance: will be polled & served right now by vanilla serveFoodToCitizen()
                tracker.clearCustomer(headId);
            }
        }

        if (pool.isEmpty()) {
            citizenToServe.clear();
            return;
        }

        // 3. Sort pool for Out-of-Order service:
        //    Priority 1: Any non-deprioritized customer already in immediate drive-by range (<= 3.5 blocks, same floor)
        //    Priority 2: Current locked active target (if still reachable/valid, prevents mid-walk ping-pong)
        //    Priority 3: Closest reachable customers by floor-weighted 3D distance
        //    Priority 4: Temporarily deprioritized (stuck/unreachable) customers at the very back of the pool
        final int lockedTargetId = tracker.activeTargetId;
        pool.sort(Comparator.comparingDouble(
                c -> computeOutOfOrderPriorityScore(cookPos, c, lockedTargetId, tracker, now)));

        // Lock onto the chosen primary target if not in immediate drive-by swap
        final AbstractEntityCitizen newHead = pool.get(0);
        if (tracker.activeTargetId != newHead.getId()) {
            tracker.setActiveTarget(newHead.getId(), cookPos.distSqr(newHead.blockPosition()));
        }

        citizenToServe.clear();
        citizenToServe.addAll(pool);
    }

    private static double computeOutOfOrderPriorityScore(
            @NotNull final BlockPos cookPos,
            @NotNull final AbstractEntityCitizen customer,
            final int lockedTargetId,
            @NotNull final CookPoolTracker tracker,
            final long now) {

        final int customerId = customer.getId();
        final BlockPos customerPos = customer.blockPosition();
        final double floorWeightedDistSqr = computeFloorWeightedDistanceSqr(cookPos, customerPos);

        // Temporarily stuck/unreachable customers sink to the back of the pool
        if (tracker.isDeprioritized(customerId, now)) {
            return DEPRIORITIZED_SORT_PENALTY + floorWeightedDistSqr;
        }

        // Opportunistic Drive-By Service: anyone within 3.5 blocks on the same floor is served immediately!
        final double rawDistSqr = cookPos.distSqr(customerPos);
        final int dy = Math.abs(cookPos.getY() - customerPos.getY());
        if (rawDistSqr <= IMMEDIATE_SERVE_RANGE_SQR && dy <= 2) {
            return rawDistSqr - 10_000.0D;
        }

        // Keep current target stable while walking toward them (unless someone is in drive-by range or target stalled)
        if (customerId == lockedTargetId) {
            return floorWeightedDistSqr * 0.65D;
        }

        return floorWeightedDistSqr;
    }

    /**
     * Deduplicates the {@code playerToServe} queue and clears it if serving players is disabled in config.
     *
     * @param playerToServe the cook's player queue.
     */
    public static void sanitizePlayerQueue(@NotNull final Queue<Player> playerToServe) {
        if (playerToServe.isEmpty()) {
            return;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.serveHungryPlayersInRestaurant.get()) {
            playerToServe.clear();
            return;
        }

        final Set<Integer> seenIds = new LinkedHashSet<>();
        final List<Player> uniquePlayers = new ArrayList<>(playerToServe.size());
        for (final Player player : playerToServe) {
            if (player != null && player.isAlive() && seenIds.add(player.getId())) {
                uniquePlayers.add(player);
            }
        }
        playerToServe.clear();
        playerToServe.addAll(uniquePlayers);
    }

    /**
     * Computes how many actions the Cook may perform before dumping inventory.
     * Vanilla returns {@code 1}, which forces the Cook to abort serving and dump after every single step.
     *
     * @param world          the world level.
     * @param cook           the cook entity.
     * @param building       the restaurant building.
     * @param citizenToServe the active customer queue.
     * @return the number of actions allowed before inventory dumping.
     */
    public static int getSmartActionsUntilDumping(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen cook,
            @NotNull final BuildingCook building,
            @NotNull final Queue<AbstractEntityCitizen> citizenToServe) {

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedRestaurantCookAI.get()) {
            return 1;
        }

        // If the cook's inventory has no free slots left, dump immediately
        if (!cook.getInventoryCitizen().hasSpace()) {
            return 1;
        }

        if (!citizenToServe.isEmpty() || hasWaitingCustomersInBuilding(world, building)) {
            return ACTIVE_SERVICE_ACTIONS_UNTIL_DUMP;
        }

        return IDLE_SERVICE_ACTIONS_UNTIL_DUMP;
    }

    private static boolean hasWaitingCustomersInBuilding(
            @NotNull final Level world,
            @NotNull final BuildingCook building) {
        for (final EntityCitizen citizen : WorldUtil.getEntitiesWithinBuilding(world, EntityCitizen.class, building, null)) {
            if (!(citizen.getCitizenJobHandler().getColonyJob() instanceof JobCook) && isCitizenStillHungry(citizen)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCitizenStillHungry(@NotNull final AbstractEntityCitizen citizen) {
        return citizen.getCitizenData() != null
                && !citizen.getCitizenData().isWorking()
                && citizen.getCitizenData().getSaturation() <= AVERAGE_SATURATION
                && !citizen.getCitizenData().justAte();
    }

    private static boolean hasValidMenuFoodInInventory(
            @NotNull final AbstractEntityCitizen citizen,
            @NotNull final BuildingCook cookBuilding,
            @NotNull final RestaurantMenuModule module) {
        return InventoryUtils.hasItemInItemHandler(
                citizen.getItemHandlerCitizen(),
                stack -> canCitizenEatMenuStack(stack, citizen, module));
    }

    private static boolean canCitizenEatMenuStack(
            @NotNull final ItemStack stack,
            @NotNull final AbstractEntityCitizen citizen,
            @NotNull final RestaurantMenuModule module) {
        if (!module.getMenu().contains(new ItemStorage(stack))) {
            return false;
        }
        if (citizen.getCitizenData() != null) {
            final IBuilding home = citizen.getCitizenData().getHomeBuilding();
            if (home != null) {
                return home.canEat(stack);
            }
        }
        return true;
    }

    /**
     * Computes squared distance with a 4x penalty on vertical difference so the waiter finishes
     * serving all customers on the current floor before walking upstairs or downstairs.
     */
    private static double computeFloorWeightedDistanceSqr(@NotNull final BlockPos from, @NotNull final BlockPos to) {
        final double dx = from.getX() - to.getX();
        final double dy = (from.getY() - to.getY()) * 4.0D;
        final double dz = from.getZ() - to.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    @NotNull
    private static CookPoolTracker getTracker(final int cookEntityId) {
        if (COOK_TRACKERS.size() > 256) {
            COOK_TRACKERS.clear();
        }
        return COOK_TRACKERS.computeIfAbsent(cookEntityId, id -> new CookPoolTracker());
    }

    /**
     * Tracks walk progress toward the current target citizen and maintains temporary cooldowns
     * for stuck or unreachable customers so they never block the dining hall queue.
     */
    private static final class CookPoolTracker {
        private int activeTargetId = -1;
        private double bestDistSqr = Double.MAX_VALUE;
        private final Map<Integer, Integer> stalledCyclesByCitizen = new HashMap<>();
        private final Map<Integer, Long> deprioritizedUntilTick = new HashMap<>();
        private final Map<Integer, Long> droppedUntilTick = new HashMap<>();

        void setActiveTarget(final int citizenId, final double initialDistSqr) {
            this.activeTargetId = citizenId;
            this.bestDistSqr = initialDistSqr;
        }

        void resetActiveTarget() {
            this.activeTargetId = -1;
            this.bestDistSqr = Double.MAX_VALUE;
        }

        int recordWalkAttempt(final int citizenId, final double currentDistSqr) {
            if (this.activeTargetId != citizenId) {
                this.activeTargetId = citizenId;
                this.bestDistSqr = currentDistSqr;
                final int prev = this.stalledCyclesByCitizen.getOrDefault(citizenId, 0) + 1;
                this.stalledCyclesByCitizen.put(citizenId, prev);
                return prev;
            }

            // If the cook moved at least ~1.2 blocks closer since the best recorded distance, count as progress
            if (currentDistSqr + 1.5D < this.bestDistSqr) {
                this.bestDistSqr = currentDistSqr;
                return this.stalledCyclesByCitizen.getOrDefault(citizenId, 0);
            }

            final int stalled = this.stalledCyclesByCitizen.getOrDefault(citizenId, 0) + 1;
            this.stalledCyclesByCitizen.put(citizenId, stalled);
            return stalled;
        }

        void markDeprioritized(final int citizenId, final long untilTick) {
            this.deprioritizedUntilTick.put(citizenId, untilTick);
        }

        void markDropped(final int citizenId, final long untilTick) {
            this.droppedUntilTick.put(citizenId, untilTick);
            this.deprioritizedUntilTick.remove(citizenId);
            this.stalledCyclesByCitizen.remove(citizenId);
        }

        boolean isDeprioritized(final int citizenId, final long now) {
            final Long until = this.deprioritizedUntilTick.get(citizenId);
            if (until == null) {
                return false;
            }
            if (now >= until) {
                this.deprioritizedUntilTick.remove(citizenId);
                return false;
            }
            return true;
        }

        boolean isDropped(final int citizenId, final long now) {
            final Long until = this.droppedUntilTick.get(citizenId);
            if (until == null) {
                return false;
            }
            if (now >= until) {
                this.droppedUntilTick.remove(citizenId);
                return false;
            }
            return true;
        }

        void clearCustomer(final int citizenId) {
            this.stalledCyclesByCitizen.remove(citizenId);
            this.deprioritizedUntilTick.remove(citizenId);
            this.droppedUntilTick.remove(citizenId);
            if (this.activeTargetId == citizenId) {
                resetActiveTarget();
            }
        }

        void cleanupExpired(final long now) {
            this.deprioritizedUntilTick.entrySet().removeIf(e -> now >= e.getValue());
            this.droppedUntilTick.entrySet().removeIf(e -> now >= e.getValue());
        }
    }
}
