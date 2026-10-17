package com.advancedcolonies.buildings.common;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.BuildingCapacityConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.crafting.AbstractCrafting;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.TavernLivingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingDeliveryman;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingHospital;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStoneSmeltery;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import net.minecraft.world.item.ItemStack;

/**
 * Evaluates configurable worker-slot scaling, Warehouse courier capacity,
 * and Residence bed limits across colony buildings.
 */
public final class BuildingCapacityHelper {

    private BuildingCapacityHelper() {
    }

    /**
     * Computes the maximum number of workers that can be assigned to the given
     * {@link WorkerBuildingModule}.
     *
     * <p>When enabled for a supported building type, capacity scales with building level:
     * Level 1–2 = 1 worker, Level 3–4 = 2 workers, Level 5 = 3 workers.</p>
     *
     * @param module     the worker building module
     * @param vanillaMax the original vanilla maximum
     * @return the effective maximum worker count
     */
    public static int computeWorkerModuleMax(final WorkerBuildingModule module, final int vanillaMax) {
        final IBuilding building = module.getBuilding();
        if (building == null) {
            return vanillaMax;
        }

        final int level = building.getBuildingLevel();
        if (level <= 0) {
            return vanillaMax;
        }

        final BuildingCapacityConfig cfg = AdvancedColoniesConfig.BUILDING_CAPACITY;
        final boolean shouldScale = (building instanceof BuildingCook && cfg.extraRestaurantCooks.get())
                || (building instanceof BuildingKitchen && cfg.extraCookhouseChefs.get())
                || (building instanceof BuildingHospital && cfg.extraHospitalHealers.get())
                || (building instanceof BuildingStoneSmeltery && cfg.extraStoneSmelteryWorkers.get())
                || (building instanceof BuildingDeliveryman && cfg.extraDeliverymanHutWorkers.get());

        if (!shouldScale) {
            return vanillaMax;
        }

        final int scaledSlots = (level + 1) / 2;
        return Math.max(vanillaMax, scaledSlots);
    }

    /**
     * Computes the maximum number of couriers that can be assigned to a Warehouse
     * at the given building level.
     *
     * @param buildingLevel the Warehouse building level
     * @param vanillaMax    the original vanilla courier limit ({@code level * 2})
     * @return the effective courier capacity
     */
    public static int computeWarehouseCourierMax(final int buildingLevel, final int vanillaMax) {
        if (buildingLevel <= 0) {
            return vanillaMax;
        }

        final BuildingCapacityConfig cfg = AdvancedColoniesConfig.BUILDING_CAPACITY;
        if (!cfg.scaleWarehouseCouriers.get()) {
            return vanillaMax;
        }

        final double multiplier = cfg.warehouseCourierMultiplier.get();
        final int scaled = (int) Math.round(buildingLevel * (buildingLevel + 1) * multiplier);
        return Math.max(1, scaled);
    }

    /**
     * Computes the maximum number of citizen beds in a residential building.
     *
     * @param module     the living building module
     * @param vanillaMax the original vanilla bed capacity
     * @return the effective resident limit
     */
    public static int computeResidenceBedMax(final LivingBuildingModule module, final int vanillaMax) {
        if (module instanceof TavernLivingBuildingModule) {
            return vanillaMax;
        }

        final IBuilding building = module.getBuilding();
        if (building == null || building.getBuildingLevel() <= 0) {
            return vanillaMax;
        }

        final int bedsPerLevel = AdvancedColoniesConfig.BUILDING_CAPACITY.residentialBedsPerLevel.get();
        if (bedsPerLevel <= 1) {
            return vanillaMax;
        }

        return building.getBuildingLevel() * bedsPerLevel;
    }

    /**
     * Checks whether any assigned Chef in a multi-worker Cookhouse ({@link BuildingKitchen})
     * is currently crafting with or producing the given food stack.
     *
     * @param kitchen the kitchen building
     * @param stack   the candidate food stack
     * @return {@code true} if the stack is reserved by an active crafting task and must not be eaten
     */
    public static boolean isReservedByAnyChef(final BuildingKitchen kitchen, final ItemStack stack) {
        final WorkerBuildingModule module = kitchen.getModule(BuildingModules.CHEF_WORK);
        if (module == null) {
            return false;
        }

        for (final ICitizenData citizenData : module.getAssignedCitizen()) {
            if (citizenData != null && citizenData.getJob() instanceof AbstractJobCrafter<?, ?> crafterJob) {
                final IRequest<? extends IRequestable> currentTask = crafterJob.getCurrentTask();
                if (currentTask != null && currentTask.getRequest() instanceof AbstractCrafting craftingRequest) {
                    final IRecipeStorage recipe = IColonyManager.getInstance()
                            .getRecipeManager()
                            .getRecipe(craftingRequest.getRecipeID());
                    if (recipe != null) {
                        if (recipe.getCleanedInput().contains(new ItemStorage(stack))
                                || ItemStack.isSameItem(recipe.getPrimaryOutput(), stack)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether any assigned Courier in a multi-worker Deliveryman Hut
     * ({@link BuildingDeliveryman}) is currently delivering the given food stack.
     *
     * @param deliveryHut the deliveryman building
     * @param stack       the candidate food stack
     * @return {@code true} if the stack is reserved by an active delivery task and must not be eaten
     */
    public static boolean isReservedByAnyCourier(final BuildingDeliveryman deliveryHut, final ItemStack stack) {
        final WorkerBuildingModule module = deliveryHut.getModule(BuildingModules.COURIER_WORK);
        if (module == null) {
            return false;
        }

        for (final ICitizenData citizenData : module.getAssignedCitizen()) {
            if (citizenData != null && citizenData.getJob() instanceof JobDeliveryman deliveryJob) {
                final IRequest<? extends IRequestable> currentTask = deliveryJob.getCurrentTask();
                if (currentTask != null
                        && currentTask.getRequest() instanceof Delivery delivery
                        && ItemStack.isSameItem(delivery.getStack(), stack)) {
                    return true;
                }
            }
        }
        return false;
    }
}
