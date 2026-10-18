package com.advancedcolonies.workers.deliveryman;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.IDeliverymanRequestable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import com.minecolonies.core.colony.buildings.modules.WarehouseRequestQueueModule;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.requestsystem.requests.StandardRequests;
import com.minecolonies.core.entity.ai.minimal.EntityAIEatTask;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.minecolonies.api.util.constant.CitizenConstants.FULL_SATURATION;

/**
 * Optimizes Courier ({@link JobDeliveryman}) logistics:
 * <ul>
 *   <li>Multi-request pickup chaining when open inventory slots remain</li>
 *   <li>Expanded same-destination parallel delivery batching</li>
 *   <li>Direct Warehouse dining using the Warehouse's food menu or general food stock</li>
 * </ul>
 */
public final class DeliverymanLogisticsOptimizer {

    /**
     * Effective secondary skill level returned during parallel delivery preparation
     * when enhanced deliveryman AI is active, allowing up to 12 parallel deliveries.
     */
    private static final int ENHANCED_PARALLEL_SKILL_LEVEL = 55;

    private DeliverymanLogisticsOptimizer() {
    }

    /**
     * Returns an elevated secondary skill level during {@code prepareDelivery()}
     * when {@code enableEnhancedDeliverymanAI} is enabled so couriers can bundle
     * more same-destination deliveries in a single trip.
     *
     * @param vanillaSkillLevel the courier's actual secondary skill level
     * @return the effective skill level for parallel delivery calculation
     */
    public static int getEffectiveParallelDeliverySkillLevel(final int vanillaSkillLevel) {
        if (!AdvancedColoniesConfig.LOGISTICS.enableEnhancedDeliverymanAI.get()) {
            return vanillaSkillLevel;
        }
        return Math.max(vanillaSkillLevel, ENHANCED_PARALLEL_SKILL_LEVEL);
    }

    /**
     * Pulls additional matching requests from the Warehouse queue when the courier
     * starts a new batch of tasks.
     *
     * @param job the deliveryman job
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void pullAdditionalBatchRequests(final JobDeliveryman job) {
        if (!AdvancedColoniesConfig.LOGISTICS.enableEnhancedDeliverymanAI.get()) {
            return;
        }

        final List<IToken<?>> currentQueue = job.getTaskQueue();
        if (currentQueue.isEmpty()) {
            return;
        }

        final IBuilding warehouse = job.findWareHouse();
        if (warehouse == null) {
            return;
        }

        final WarehouseRequestQueueModule queueModule = warehouse.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
        if (queueModule == null || queueModule.getMutableRequestList().isEmpty()) {
            return;
        }

        final IToken<?> firstToken = currentQueue.get(0);
        final IRequest<?> firstReq = job.getColony().getRequestManager().getRequestForToken(firstToken);
        if (firstReq == null) {
            return;
        }

        // If the primary request is a DeliveryRequest, allow up to 12 total same-destination deliveries
        if (firstReq instanceof StandardRequests.DeliveryRequest && job instanceof IJobDeliverymanAccess access) {
            final LinkedList<IToken<?>> mutableQueue = access.advancedColonies$getMutableTaskQueue();
            if (mutableQueue == null) {
                return;
            }
            int count = mutableQueue.size();
            final List<IToken<?>> toRemove = new ArrayList<>();
            for (final IToken<?> candidateToken : queueModule.getMutableRequestList()) {
                if (count >= 12) {
                    break;
                }
                if (mutableQueue.contains(candidateToken)) {
                    continue;
                }
                final IRequest localRequest = job.getColony().getRequestManager().getRequestForToken(candidateToken);
                if (localRequest instanceof StandardRequests.DeliveryRequest
                        && hasSameDestinationInQueue(job, access, mutableQueue, (IRequest<? extends Delivery>) localRequest)) {
                    mutableQueue.add(Math.min(count, mutableQueue.size()), candidateToken);
                    toRemove.add(candidateToken);
                    count++;
                }
            }
            if (!toRemove.isEmpty()) {
                queueModule.getMutableRequestList().removeAll(toRemove);
                queueModule.markDirty();
            }
        }
    }

    private static boolean hasSameDestinationInQueue(
            final JobDeliveryman job,
            final IJobDeliverymanAccess access,
            final List<IToken<?>> queue,
            final IRequest<? extends Delivery> candidateRequest) {
        final Delivery candidateDelivery = candidateRequest.getRequest();
        if (candidateDelivery == null) {
            return false;
        }
        for (final IToken<?> existingToken : queue) {
            final IRequest<?> existingReq = job.getColony().getRequestManager().getRequestForToken(existingToken);
            if (existingReq != null && existingReq.getRequest() instanceof Delivery existingDelivery) {
                if (access.advancedColonies$haveTasksSameSourceAndDest(existingDelivery, candidateDelivery)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Attempts to chain another hut pickup immediately after completing a {@code PickupRequest}
     * if the courier still has free inventory space.
     *
     * @param job    the courier job
     * @param worker the courier entity
     * @param full   whether {@code cannotHoldMoreItems()} is true for the courier's hut level
     * @return {@link AIWorkerState#PICKUP} if another pickup was chained, or {@code null} to use default transition
     */
    @Nullable
    public static IAIState tryChainNextPickup(
            final JobDeliveryman job,
            final AbstractEntityCitizen worker,
            final boolean full) {
        if (!AdvancedColoniesConfig.LOGISTICS.enableEnhancedDeliverymanAI.get() || full) {
            return null;
        }

        if (InventoryUtils.openSlotCount(worker.getInventoryCitizen()) <= 1) {
            return null;
        }

        // Check if the next queued task is already a PickupRequest
        final IRequest<? extends IDeliverymanRequestable> nextQueued = job.getCurrentTask();
        if (nextQueued instanceof StandardRequests.PickupRequest) {
            return AIWorkerState.PICKUP;
        }

        // If the personal queue is empty, look for the closest pending PickupRequest in the Warehouse queue
        if (nextQueued == null && job instanceof IJobDeliverymanAccess access) {
            final LinkedList<IToken<?>> mutableQueue = access.advancedColonies$getMutableTaskQueue();
            final IBuilding warehouse = job.findWareHouse();
            if (warehouse != null && mutableQueue != null) {
                final WarehouseRequestQueueModule queueModule = warehouse.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
                if (queueModule != null && !queueModule.getMutableRequestList().isEmpty()) {
                    final BlockPos workerPos = worker.blockPosition();
                    IToken<?> bestPickupToken = null;
                    double bestDistSq = Double.MAX_VALUE;

                    for (final IToken<?> token : queueModule.getMutableRequestList()) {
                        final IRequest<?> req = job.getColony().getRequestManager().getRequestForToken(token);
                        if (req instanceof StandardRequests.PickupRequest) {
                            final BlockPos targetPos = req.getRequester().getLocation().getInDimensionLocation();
                            final double distSq = workerPos.distSqr(targetPos);
                            if (distSq < bestDistSq) {
                                bestDistSq = distSq;
                                bestPickupToken = token;
                            }
                        }
                    }

                    if (bestPickupToken != null) {
                        mutableQueue.addFirst(bestPickupToken);
                        queueModule.getMutableRequestList().remove(bestPickupToken);
                        queueModule.markDirty();
                        return AIWorkerState.PICKUP;
                    }
                }
            }
        }

        // If the courier's inventory is not empty and no more pickups exist, dump at the warehouse now
        if (!worker.getInventoryCitizen().isEmpty()) {
            return AIWorkerState.DUMPING;
        }

        return null;
    }

    /**
     * Allows a hungry Courier ({@link JobDeliveryman}) to walk to their assigned Warehouse
     * and eat food directly from the Warehouse inventory (prioritizing the Warehouse's food menu
     * if configured) instead of walking to a Restaurant.
     *
     * @param citizen the eating citizen entity
     * @return {@link EntityAIEatTask.EatingState#GO_TO_HUT} while walking to the Warehouse,
     *         {@link EntityAIEatTask.EatingState#EAT} once food is acquired,
     *         or {@code null} if the citizen is not a courier or the Warehouse has no food
     */
    @Nullable
    public static EntityAIEatTask.EatingState tryEatAtWarehouse(final EntityCitizen citizen) {
        if (!AdvancedColoniesConfig.LOGISTICS.deliverymanEatAtWarehouse.get()) {
            return null;
        }

        final ICitizenData citizenData = citizen.getCitizenData();
        if (citizenData == null || !(citizenData.getJob() instanceof JobDeliveryman deliveryJob)) {
            return null;
        }

        final IWareHouse warehouse = deliveryJob.findWareHouse();
        if (warehouse == null) {
            return null;
        }

        // Check if the courier already has edible food in their inventory
        final RestaurantMenuModule menuModule = warehouse.getModule(BuildingModules.NETHERMINER_MENU);
        final Set<ItemStorage> menu = (menuModule != null && !menuModule.getMenu().isEmpty())
                ? menuModule.getMenu()
                : null;

        int existingSlot = FoodUtils.getBestFoodForCitizen(citizen.getInventoryCitizen(), citizenData, menu);
        if (existingSlot == -1 && menu != null) {
            existingSlot = FoodUtils.getBestFoodForCitizen(citizen.getInventoryCitizen(), citizenData, null);
        }
        if (existingSlot != -1) {
            return EntityAIEatTask.EatingState.EAT;
        }

        // Check if the Warehouse has edible food before walking there
        ItemStorage storageToGet = FoodUtils.checkForFoodInBuilding(citizenData, menu, warehouse);
        if (storageToGet == null && menu != null) {
            storageToGet = FoodUtils.checkForFoodInBuilding(citizenData, null, warehouse);
        }
        if (storageToGet == null) {
            return null;
        }

        if (!EntityNavigationUtils.walkToBuilding(citizen, warehouse)) {
            return EntityAIEatTask.EatingState.GO_TO_HUT;
        }

        final double foodValue = Math.max(1.0D, FoodUtils.getFoodValue(storageToGet.getItemStack(), citizen));
        final int qty = Math.max(1, (int) Math.ceil((FULL_SATURATION - citizenData.getSaturation()) / foodValue));
        if (InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(
                warehouse,
                storageToGet,
                qty,
                citizen.getInventoryCitizen())) {
            return EntityAIEatTask.EatingState.EAT;
        }

        return null;
    }
}
