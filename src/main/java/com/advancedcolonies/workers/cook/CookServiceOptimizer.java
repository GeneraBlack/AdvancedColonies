package com.advancedcolonies.workers.cook;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.jobs.JobCook;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static com.minecolonies.api.util.constant.CitizenConstants.AVERAGE_SATURATION;

/**
 * Optimizes the Restaurant Cook / Waiter workflow ({@code EntityAIWorkCook}):
 * <ul>
 *   <li><b>Queue Deduplication:</b> Prevents adding the same waiting citizen or player multiple times
 *       when the cook fetches additional ingredients from the racks.</li>
 *   <li><b>Nearest-Table-First Sorting:</b> Sorts the waiting customer queue by 3D distance to the cook
 *       (with vertical floor weighting so the cook serves all tables on the current floor before taking stairs).</li>
 *   <li><b>Smart Inventory Dumping:</b> Prevents the cook from dumping their inventory after every single
 *       action (`getActionsDoneUntilDumping() == 1` in vanilla) while hungry customers are waiting.</li>
 * </ul>
 */
public final class CookServiceOptimizer {

    /** Actions allowed before dumping when customers are actively waiting in the restaurant. */
    private static final int ACTIVE_SERVICE_ACTIONS_UNTIL_DUMP = 64;

    /** Default actions allowed before dumping when no customers are waiting. */
    private static final int IDLE_SERVICE_ACTIONS_UNTIL_DUMP = 16;

    private CookServiceOptimizer() {
        // Utility class
    }

    /**
     * Deduplicates and sorts the {@code citizenToServe} queue so the Cook serves the closest
     * valid waiting citizen on the current floor first.
     *
     * @param cook           the cook worker entity.
     * @param building       the restaurant building.
     * @param citizenToServe the cook's customer queue to optimize in-place.
     */
    public static void deduplicateAndSortCitizenQueue(
            @NotNull final AbstractEntityCitizen cook,
            @NotNull final BuildingCook building,
            @NotNull final Queue<AbstractEntityCitizen> citizenToServe) {

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedRestaurantCookAI.get()
                || citizenToServe.isEmpty()) {
            return;
        }

        // 1. Filter out invalid/already-fed/outside-building entries and deduplicate by entity ID
        final Set<Integer> seenIds = new LinkedHashSet<>();
        final List<AbstractEntityCitizen> uniqueValid = new ArrayList<>(citizenToServe.size());
        for (final AbstractEntityCitizen candidate : citizenToServe) {
            if (candidate == null || !candidate.isAlive()) {
                continue;
            }
            if (!building.isInBuilding(candidate.blockPosition()) || !isCitizenStillHungry(candidate)) {
                continue;
            }
            if (seenIds.add(candidate.getId())) {
                uniqueValid.add(candidate);
            }
        }

        // 2. Sort by floor-weighted distance to the cook so tables on the same floor are served first
        final BlockPos cookPos = cook.blockPosition();
        uniqueValid.sort(Comparator.comparingDouble(c -> computeFloorWeightedDistanceSqr(cookPos, c.blockPosition())));

        citizenToServe.clear();
        citizenToServe.addAll(uniqueValid);
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
}
