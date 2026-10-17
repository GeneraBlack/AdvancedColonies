package com.advancedcolonies.buildings.restaurant;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MathUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.other.SittingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.util.constant.SchematicTagConstants.TAG_SITTING;
import static com.minecolonies.api.util.constant.SchematicTagConstants.TAG_SIT_IN;
import static com.minecolonies.api.util.constant.SchematicTagConstants.TAG_SIT_OUT;

/**
 * Manages seat selection and load-balanced restaurant choice for Dining Halls ({@link BuildingCook}).
 * <p>
 * Fixes two major vanilla bottlenecks:
 * <ul>
 *   <li><b>Reliable Free Seat Lookup:</b> Vanilla only makes 3 random attempts to find an unoccupied chair
 *       and returns {@code null} if all 3 random picks are occupied, even when many other seats are free.
 *       This helper collects all eligible seats and guarantees finding an unoccupied seat if one exists.</li>
 *   <li><b>Load-Balanced Restaurant Selection:</b> Evaluates both distance and current customer occupancy
 *       when selecting a restaurant so citizens do not crowd a single Dining Hall.</li>
 * </ul>
 */
public final class RestaurantSeatSelector {

    /** Equivalent distance penalty (in blocks) added per waiting customer inside a restaurant. */
    private static final double CROWD_PENALTY_BLOCKS_PER_CUSTOMER = 25.0D;

    private RestaurantSeatSelector() {
        // Utility class
    }

    /**
     * Finds an unoccupied sitting position inside (or outside when not raining) the given restaurant.
     *
     * @param restaurant the {@link BuildingCook} instance.
     * @return an unoccupied sitting {@link BlockPos}, or {@code null} if every eligible seat is occupied.
     */
    @Nullable
    public static BlockPos findAvailableSittingPosition(@NotNull final BuildingCook restaurant) {
        final IColony colony = restaurant.getColony();
        if (colony == null || colony.getWorld() == null) {
            return null;
        }

        final Level world = colony.getWorld();
        final List<BlockPos> candidates = new ArrayList<>();
        candidates.addAll(restaurant.getLocationsFromTag(TAG_SITTING));
        candidates.addAll(restaurant.getLocationsFromTag(TAG_SIT_IN));
        if (!world.isRaining()) {
            candidates.addAll(restaurant.getLocationsFromTag(TAG_SIT_OUT));
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Start at a random index to distribute wear/seating evenly, then probe all seats linearly
        final int size = candidates.size();
        final int startIndex = MathUtils.RANDOM.nextInt(size);
        for (int offset = 0; offset < size; offset++) {
            final BlockPos pos = candidates.get((startIndex + offset) % size);
            if (!SittingEntity.isSittingPosOccupied(pos, world)) {
                return pos;
            }
        }

        return null;
    }

    /**
     * Selects the best restaurant for a hungry citizen by balancing travel distance against
     * current customer crowding inside each restaurant.
     *
     * @param citizen    the hungry citizen searching for a restaurant.
     * @param searchFrom the reference origin position (work building, home building, or citizen position).
     * @return the position of the best restaurant, or {@code null} if the colony has no restaurants.
     */
    @Nullable
    public static BlockPos selectLeastCrowdedRestaurant(
            @NotNull final EntityCitizen citizen,
            @NotNull final BlockPos searchFrom) {

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.preorderRestaurantTable.get()) {
            return null;
        }

        final IColony colony = citizen.getCitizenData() != null ? citizen.getCitizenData().getColony() : null;
        if (colony == null || colony.getServerBuildingManager() == null || colony.getWorld() == null) {
            return null;
        }

        final Level world = colony.getWorld();
        BlockPos bestStaffedPos = null;
        double bestStaffedScore = Double.MAX_VALUE;

        BlockPos bestUnstaffedPos = null;
        double bestUnstaffedScore = Double.MAX_VALUE;

        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
            if (!(building instanceof BuildingCook cookBuilding) || cookBuilding.getBuildingLevel() <= 0) {
                continue;
            }

            final double dist = BlockPosUtil.dist(searchFrom, cookBuilding.getPosition());
            final int occupants = WorldUtil.getEntitiesWithinBuilding(world, EntityCitizen.class, cookBuilding, null).size();
            final double score = dist + (occupants * CROWD_PENALTY_BLOCKS_PER_CUSTOMER);

            final boolean staffed = cookBuilding.getModule(BuildingModules.COOK_WORK) != null
                    && cookBuilding.getModule(BuildingModules.COOK_WORK).hasAssignedCitizen();

            if (staffed) {
                if (score < bestStaffedScore) {
                    bestStaffedScore = score;
                    bestStaffedPos = cookBuilding.getPosition();
                }
            } else if (score < bestUnstaffedScore) {
                bestUnstaffedScore = score;
                bestUnstaffedPos = cookBuilding.getPosition();
            }
        }

        return bestStaffedPos != null ? bestStaffedPos : bestUnstaffedPos;
    }
}
