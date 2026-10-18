package com.advancedcolonies.workers.cook;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.ICitizenData;
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
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.CitizenConstants.AVERAGE_SATURATION;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.RESTAURANT_MENU;

/**
 * Optimizes the Restaurant / Dining Hall Cook & Waiter workflow ({@code EntityAIWorkCook})
 * by transforming the vanilla FIFO queue into a <b>non-blocking, out-of-order customer pool</b>:
 * <ul>
 *   <li><b>Out-of-Order Pool & Opportunistic Drive-By Service:</b> Any waiting citizen who is already
 *       within immediate serving reach ({@code <= 4.0} blocks on the same floor) is served
 *       immediately so the cook feeds nearby guests on the fly without waiting on distant targets.</li>
 *   <li><b>SittingEntity Floor-Offset Compensation:</b> Compensates for {@code SittingEntity#sitDown}
 *       lowering a sitting citizen's Y coordinate by {@code (bbHeight / 2) + 0.25} into the floor block
 *       underneath the chair, preventing ground-floor guests from failing {@code building.isInBuilding}
 *       or making the cook pathfind into the floor below.</li>
 *   <li><b>Stuck / Unreachable Customer Bypass (No Head-of-Line Blocking):</b> Tracks consecutive
 *       walk attempts per target citizen. If a customer cannot be reached within {@link #MAX_WALK_ATTEMPTS_BEFORE_BYPASS}
 *       stalled cycles (e.g. stuck in a chair/corner or pathfinding fails), that citizen receives a temporary
 *       deprioritization cooldown and moves to the back of the pool so all other reachable citizens
 *       are served out-of-order first.</li>
 *   <li><b>Non-Blocking Inventory Service:</b> Ensures that if the cook already carries valid menu food for
 *       waiting guests, they are added to the pool and served immediately without a single guest's missing
 *       rack food blocking the dining hall.</li>
 *   <li><b>Smart Inventory Dumping:</b> Prevents the cook from dumping their inventory after every single
 *       action ({@code getActionsDoneUntilDumping() == 1} in vanilla) while actively serving customers,
 *       while still dumping cooked furnace output into racks when the customer queue is empty.</li>
 * </ul>
 */
public final class CookServiceOptimizer {

    /** Actions allowed before dumping while customers are actively being served from the pool. */
    private static final int ACTIVE_SERVICE_ACTIONS_UNTIL_DUMP = 64;

    /**
     * Maximum consecutive stalled {@code serveFoodToCitizen} cycles (30 ticks = 1.5s each, so 6 cycles = 9.0s
     * of zero movement progress) before a target customer is considered temporarily stuck/unreachable and
     * bypassed out-of-order.
     */
    private static final int MAX_WALK_ATTEMPTS_BEFORE_BYPASS = 6;

    /**
     * Maximum bypass rounds before a completely unreachable customer is temporarily dropped from the pool
     * so the cook is never deadlocked.
     */
    private static final int MAX_BYPASS_ROUNDS_BEFORE_DROP = 2;

    /** Ticks a bypassed stuck customer stays deprioritized in the pool (300 ticks = 15 seconds). */
    private static final long UNREACHABLE_COOLDOWN_TICKS = 300L;

    /** Squared 3D distance within which a customer at a table/seat can be served immediately (4.0 blocks). */
    private static final double IMMEDIATE_SERVE_RANGE_SQR = 4.0D * 4.0D;

    /** Maximum vertical block difference for immediate table/drive-by serving on the same floor. */
    private static final int IMMEDIATE_SERVE_MAX_DY = 2;

    /** Sorting penalty added to temporarily unreachable/stuck customers so reachable guests always come first. */
    private static final double DEPRIORITIZED_SORT_PENALTY = 1_000_000.0D;

    /** Per-cook tracking state for out-of-order pool management and stuck-customer bypass. */
    private static final Map<Integer, CookPoolTracker> COOK_TRACKERS = new ConcurrentHashMap<>();

    private CookServiceOptimizer() {
        // Utility class
    }

    /**
     * Resolves the effective block position of a customer in the dining hall, compensating for
     * {@code SittingEntity#sitDown} lowering a seated citizen's Y coordinate by {@code (bbHeight / 2) + 0.25}
     * (which otherwise places {@code customer.blockPosition()} 1–2 blocks below the chair inside the floor).
     *
     * @param customer    the customer entity.
     * @param fallbackPos fallback position if {@code customer} is {@code null}.
     * @return the effective floor/chair block position of the customer.
     */
    @Nullable
    public static BlockPos getEffectiveCustomerPos(
            @Nullable final AbstractEntityCitizen customer,
            @Nullable final BlockPos fallbackPos) {
        if (customer == null) {
            return fallbackPos;
        }
        if (customer.isPassenger()) {
            final int chairY = Mth.floor(customer.getY() + (customer.getBbHeight() * 0.5D) + 0.26D);
            return new BlockPos(customer.getBlockX(), chairY, customer.getBlockZ());
        }
        return customer.blockPosition();
    }

    /**
     * Checks whether a customer (including one seated in a {@code SittingEntity} chair on the lowest
     * floor of a restaurant blueprint) is inside {@code building}.
     *
     * @param building    the restaurant building.
     * @param customer    the customer entity (may be {@code null}).
     * @param fallbackPos the raw block position passed by vanilla.
     * @return {@code true} if the customer is inside the building.
     */
    public static boolean isCustomerInBuilding(
            @NotNull final BuildingCook building,
            @Nullable final AbstractEntityCitizen customer,
            @Nullable final BlockPos fallbackPos) {
        if (fallbackPos != null && building.isInBuilding(fallbackPos)) {
            return true;
        }
        if (customer == null) {
            return false;
        }
        final BlockPos effectivePos = getEffectiveCustomerPos(customer, fallbackPos);
        if (effectivePos != null && building.isInBuilding(effectivePos)) {
            return true;
        }
        final BlockPos rawPos = customer.blockPosition();
        return building.isInBuilding(rawPos.above()) || building.isInBuilding(rawPos.above(2));
    }

    /**
     * Checks whether the cook is close enough to the customer's position to hand over food directly
     * without requiring vanilla's strict arrival check to complete.
     *
     * @param cook      the cook worker entity.
     * @param targetPos the target customer block position.
     * @return {@code true} if within immediate serving range on the same floor.
     */
    public static boolean isWithinImmediateServeRange(
            @NotNull final AbstractEntityCitizen cook,
            @Nullable final BlockPos targetPos) {
        if (targetPos == null || !AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedRestaurantCookAI.get()) {
            return false;
        }
        final int dy = Math.abs(cook.getBlockY() - targetPos.getY());
        if (dy > IMMEDIATE_SERVE_MAX_DY) {
            return false;
        }
        return cook.blockPosition().distSqr(targetPos) <= IMMEDIATE_SERVE_RANGE_SQR;
    }

    /**
     * Notifies the optimizer that a customer was reached and is about to be served and polled from the queue.
     *
     * @param cook           the cook worker entity.
     * @param servedCustomer the customer being served.
     */
    public static void onCustomerServed(
            @NotNull final AbstractEntityCitizen cook,
            @Nullable final AbstractEntityCitizen servedCustomer) {
        if (servedCustomer == null) {
            return;
        }
        final CookPoolTracker tracker = getTracker(cook.getId());
        tracker.clearCustomer(servedCustomer.getId());
    }

    /**
     * Unified edibility check matching {@code FoodUtils.canEat(stack, homeBuilding, workBuilding)}
     * (used by {@code EntityAIEatTask#hasFood()} and {@code FoodUtils.getBestFoodForCitizen})
     * so the Cook and the Citizen never disagree by 1 hut level on whether a food item is edible.
     *
     * @param stack   the candidate food stack.
     * @param citizen the target citizen.
     * @return {@code true} if the citizen is allowed to eat {@code stack}.
     */
    public static boolean canCitizenEatStack(
            @NotNull final ItemStack stack,
            @Nullable final AbstractEntityCitizen citizen) {
        if (citizen == null || citizen.getCitizenData() == null) {
            return true;
        }
        final ICitizenData data = citizen.getCitizenData();
        return FoodUtils.canEat(stack, data.getHomeBuilding(), data.getWorkBuilding());
    }

    /**
     * Checks whether the customer's inventory already contains a menu food item that the customer
     * is actually allowed to eat according to their home and work building levels.
     * <p>
     * Fixes the bug in {@code EntityAIWorkCook#serveFoodToCitizen()} where vanilla checked
     * {@code module.getMenu().contains(new ItemStorage(stack))} without checking {@code canEat},
     * causing the cook to walk up to a hungry citizen holding a rejected low-tier menu item and
     * abort without giving them valid food.
     */
    public static boolean hasValidMenuFoodInCustomerHandler(
            @Nullable final IItemHandler citizenInventory,
            @NotNull final Predicate<ItemStack> rawMenuPredicate,
            @NotNull final BuildingCook building,
            @Nullable final AbstractEntityCitizen customer) {
        if (citizenInventory == null) {
            return false;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedRestaurantCookAI.get() || customer == null) {
            return InventoryUtils.hasItemInItemHandler(citizenInventory, rawMenuPredicate);
        }
        final RestaurantMenuModule module = building.getModule(RESTAURANT_MENU);
        if (module == null) {
            return InventoryUtils.hasItemInItemHandler(citizenInventory, rawMenuPredicate);
        }
        return hasValidMenuFoodInInventory(customer, module);
    }

    /**
     * Called at the return of {@code EntityAIWorkCook#checkForImportantJobs()}.
     * Populates the customer pool with all citizens who can be fed from the cook's current inventory,
     * deduplicates/sorts the pool out-of-order, and transitions to serving when ready customers are waiting.
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

        // Collect all hungry citizens in the dining hall for whom the cook ALREADY carries valid menu food.
        collectReadyCustomersIntoPool(world, cook, building, citizenToServe);

        // Deduplicate, filter out citizens without matching food in the cook's inventory, and sort out-of-order.
        refreshCitizenPoolOutOfOrder(world, cook, building, citizenToServe, false);

        // If the cook already holds valid food for waiting customers in the pool, serve them immediately
        // instead of letting a single citizen's rack-gathering request block service!
        if (!citizenToServe.isEmpty()
                && (originalState == AIWorkerState.START_WORKING
                || originalState == AIWorkerState.GATHERING_REQUIRED_MATERIALS
                || originalState == AIWorkerState.COOK_SERVE_FOOD_TO_CITIZEN)) {
            return AIWorkerState.COOK_SERVE_FOOD_TO_CITIZEN;
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

        // Include any newly arrived hungry citizen in the dining hall whom the cook can feed from current inventory
        collectReadyCustomersIntoPool(world, cook, building, citizenToServe);

        if (citizenToServe.isEmpty()) {
            return;
        }

        refreshCitizenPoolOutOfOrder(world, cook, building, citizenToServe, true);
    }

    /**
     * Scans the building for hungry citizens for whom the cook currently carries a valid menu item
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
                    || hasValidMenuFoodInInventory(citizen, module)) {
                continue;
            }
            if (tracker.isDropped(citizen.getId(), now)) {
                continue;
            }
            if (hasServeableFoodInCookInventory(cook, citizen, module)) {
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

        // 1. Deduplicate and filter valid hungry citizens still inside the building for whom the cook carries food
        final Set<Integer> seenIds = new LinkedHashSet<>();
        final List<AbstractEntityCitizen> pool = new ArrayList<>(citizenToServe.size());
        for (final AbstractEntityCitizen candidate : citizenToServe) {
            if (candidate == null || !candidate.isAlive()) {
                continue;
            }
            if (!isCustomerInBuilding(building, candidate, candidate.blockPosition())
                    || !isCitizenStillHungry(candidate)) {
                tracker.clearCustomer(candidate.getId());
                continue;
            }
            if (module != null) {
                if (hasValidMenuFoodInInventory(candidate, module)) {
                    tracker.clearCustomer(candidate.getId());
                    continue;
                }
                if (!hasServeableFoodInCookInventory(cook, candidate, module)) {
                    // Cook does not currently carry food that this citizen can eat; skip for this trip
                    continue;
                }
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

        // 2. Evaluate walk progress toward the current active target during serveFoodToCitizen ticks
        if (isServeTick) {
            final AbstractEntityCitizen currentHead = pool.get(0);
            final int headId = currentHead.getId();
            final BlockPos headPos = getEffectiveCustomerPos(currentHead, currentHead.blockPosition());

            if (!isWithinImmediateServeRange(cook, headPos)) {
                final double distSqr = cookPos.distSqr(headPos);
                final boolean pathComputing = isPathStillComputing(cook);
                final int stalledCycles = tracker.recordWalkAttempt(headId, distSqr, pathComputing);

                if (stalledCycles >= MAX_WALK_ATTEMPTS_BEFORE_BYPASS) {
                    final int bypassRounds = tracker.recordBypassRound(headId);
                    if (bypassRounds >= MAX_BYPASS_ROUNDS_BEFORE_DROP) {
                        // Customer has been completely unreachable across multiple rounds; drop temporarily
                        // so the cook is never deadlocked and can resume stocking/cooking.
                        tracker.markDropped(headId, now + UNREACHABLE_COOLDOWN_TICKS);
                        cook.getNavigation().stop();
                        pool.remove(0);
                        tracker.resetActiveTarget();
                    } else {
                        // Bypass this stuck customer out-of-order so other reachable guests are served first!
                        tracker.markDeprioritized(headId, now + UNREACHABLE_COOLDOWN_TICKS);
                        if (pool.size() > 1) {
                            cook.getNavigation().stop();
                            tracker.resetActiveTarget();
                        }
                    }
                }
            }
        }

        if (pool.isEmpty()) {
            citizenToServe.clear();
            return;
        }

        // 3. Sort pool for Out-of-Order service:
        //    Priority 1: Any non-deprioritized customer already in immediate serve range (<= 4.0 blocks, same floor)
        //    Priority 2: Current locked active target (prevents mid-walk target switching that resets pathfinding)
        //    Priority 3: Closest reachable customers by floor-weighted 3D distance
        //    Priority 4: Temporarily deprioritized (stuck/unreachable) customers at the very back of the pool
        final int lockedTargetId = tracker.activeTargetId;
        pool.sort(Comparator.comparingDouble(
                c -> computeOutOfOrderPriorityScore(cook, cookPos, c, lockedTargetId, tracker, now)));

        final AbstractEntityCitizen newHead = pool.get(0);
        final BlockPos newHeadPos = getEffectiveCustomerPos(newHead, newHead.blockPosition());
        if (tracker.activeTargetId != newHead.getId()) {
            tracker.setActiveTarget(newHead.getId(), cookPos.distSqr(newHeadPos));
        }

        citizenToServe.clear();
        citizenToServe.addAll(pool);
    }

    private static double computeOutOfOrderPriorityScore(
            @NotNull final AbstractEntityCitizen cook,
            @NotNull final BlockPos cookPos,
            @NotNull final AbstractEntityCitizen customer,
            final int lockedTargetId,
            @NotNull final CookPoolTracker tracker,
            final long now) {

        final int customerId = customer.getId();
        final BlockPos customerPos = getEffectiveCustomerPos(customer, customer.blockPosition());
        final double floorWeightedDistSqr = computeFloorWeightedDistanceSqr(cookPos, customerPos);

        // Temporarily stuck/unreachable customers sink to the back of the pool
        if (tracker.isDeprioritized(customerId, now)) {
            return DEPRIORITIZED_SORT_PENALTY + floorWeightedDistSqr;
        }

        // Opportunistic Drive-By Service: anyone within immediate serving range (<= 4.0 blocks, dy <= 2)
        // is served immediately on this very tick without restarting navigation!
        if (isWithinImmediateServeRange(cook, customerPos)) {
            return cookPos.distSqr(customerPos) - 100_000.0D;
        }

        // Keep the current walk target strictly locked while walking toward them so pathfinding never resets mid-walk
        if (customerId == lockedTargetId) {
            return -50_000.0D;
        }

        return floorWeightedDistSqr;
    }

    private static boolean isPathStillComputing(@NotNull final AbstractEntityCitizen cook) {
        if (cook.getNavigation() instanceof MinecoloniesAdvancedPathNavigate nav) {
            return nav.getPathResult() != null && nav.getPathResult().isComputing();
        }
        return false;
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
     * Computes how many actions the Cook may perform before dumping inventory into the restaurant racks.
     * <p>
     * Note: {@code EntityAIWorkCook} increments {@code actionsDone} in {@code extractFromFurnace()}.
     * When {@code citizenToServe} is empty, returning {@code 1} ensures freshly cooked food from the furnaces
     * is immediately dumped into the restaurant racks (so citizens and {@code checkForFoodInBuilding} can find it).
     * While customers are actively in {@code citizenToServe}, returning {@link #ACTIVE_SERVICE_ACTIONS_UNTIL_DUMP}
     * prevents a pending furnace extraction counter from interrupting customer service.
     *
     * @param cook           the cook entity.
     * @param citizenToServe the active customer queue.
     * @return the number of actions allowed before inventory dumping.
     */
    public static int getSmartActionsUntilDumping(
            @NotNull final AbstractEntityCitizen cook,
            @NotNull final Queue<AbstractEntityCitizen> citizenToServe) {

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedRestaurantCookAI.get()) {
            return 1;
        }

        if (!cook.getInventoryCitizen().hasSpace()) {
            return 1;
        }

        if (!citizenToServe.isEmpty()) {
            return ACTIVE_SERVICE_ACTIONS_UNTIL_DUMP;
        }

        return 1;
    }

    private static boolean hasServeableFoodInCookInventory(
            @NotNull final AbstractEntityCitizen cook,
            @NotNull final AbstractEntityCitizen customer,
            @NotNull final RestaurantMenuModule module) {
        if (customer.getCitizenData() == null) {
            return false;
        }
        return FoodUtils.getBestFoodForCitizen(
                cook.getInventoryCitizen(),
                customer.getCitizenData(),
                module.getMenu()) != -1;
    }

    private static boolean isCitizenStillHungry(@NotNull final AbstractEntityCitizen citizen) {
        return citizen.getCitizenData() != null
                && !citizen.getCitizenData().isWorking()
                && citizen.getCitizenData().getSaturation() <= AVERAGE_SATURATION
                && !citizen.getCitizenData().justAte();
    }

    private static boolean hasValidMenuFoodInInventory(
            @NotNull final AbstractEntityCitizen citizen,
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
        return canCitizenEatStack(stack, citizen);
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
        private boolean awaitingFirstWalkTick = true;
        private final Map<Integer, Integer> stalledCyclesByCitizen = new HashMap<>();
        private final Map<Integer, Integer> bypassRoundsByCitizen = new HashMap<>();
        private final Map<Integer, Long> deprioritizedUntilTick = new HashMap<>();
        private final Map<Integer, Long> droppedUntilTick = new HashMap<>();

        void setActiveTarget(final int citizenId, final double initialDistSqr) {
            this.activeTargetId = citizenId;
            this.bestDistSqr = initialDistSqr;
            this.awaitingFirstWalkTick = true;
        }

        void resetActiveTarget() {
            this.activeTargetId = -1;
            this.bestDistSqr = Double.MAX_VALUE;
            this.awaitingFirstWalkTick = true;
        }

        int recordWalkAttempt(final int citizenId, final double currentDistSqr, final boolean pathComputing) {
            if (this.activeTargetId != citizenId) {
                setActiveTarget(citizenId, currentDistSqr);
                this.awaitingFirstWalkTick = false;
                return 0;
            }

            // Do not count the very first head-hook invocation before walkToWorkPos() has even started
            if (this.awaitingFirstWalkTick) {
                this.awaitingFirstWalkTick = false;
                this.bestDistSqr = Math.min(this.bestDistSqr, currentDistSqr);
                return 0;
            }

            // If the cook moved closer to the target, reset stalled cycles and record new best distance
            if (currentDistSqr + 0.75D < this.bestDistSqr) {
                this.bestDistSqr = currentDistSqr;
                this.stalledCyclesByCitizen.put(citizenId, 0);
                return 0;
            }

            // Do not penalize while the async pathfinder is still computing the route
            if (pathComputing) {
                return this.stalledCyclesByCitizen.getOrDefault(citizenId, 0);
            }

            final int stalled = this.stalledCyclesByCitizen.getOrDefault(citizenId, 0) + 1;
            this.stalledCyclesByCitizen.put(citizenId, stalled);
            return stalled;
        }

        int recordBypassRound(final int citizenId) {
            this.stalledCyclesByCitizen.put(citizenId, 0);
            final int rounds = this.bypassRoundsByCitizen.getOrDefault(citizenId, 0) + 1;
            this.bypassRoundsByCitizen.put(citizenId, rounds);
            return rounds;
        }

        void markDeprioritized(final int citizenId, final long untilTick) {
            this.deprioritizedUntilTick.put(citizenId, untilTick);
            this.stalledCyclesByCitizen.put(citizenId, 0);
        }

        void markDropped(final int citizenId, final long untilTick) {
            this.droppedUntilTick.put(citizenId, untilTick);
            this.deprioritizedUntilTick.remove(citizenId);
            this.stalledCyclesByCitizen.remove(citizenId);
            this.bypassRoundsByCitizen.remove(citizenId);
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
            this.bypassRoundsByCitizen.remove(citizenId);
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
