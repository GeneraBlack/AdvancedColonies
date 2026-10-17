package com.advancedcolonies.workers.builder;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.BuilderAndExcavationConfig.WorkMode;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.builder.EntityAIStructureBuilder;
import com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier;
import com.minecolonies.core.entity.ai.workers.production.EntityAIStructureMiner;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Evaluates configurable work positioning modes ({@link WorkMode}) and remote order acceptance
 * for Builders, Miners, and Quarriers.
 */
public final class StructureWorkModeHelper {

    /** Horizontal and vertical radius around the work-order center accepted in SENTRY mode. */
    private static final int SENTRY_ANCHOR_RADIUS = 8;

    private StructureWorkModeHelper() {
    }

    /**
     * Resolves the active {@link WorkMode} for a given worker AI instance.
     *
     * @param ai the worker AI instance
     * @return the configured {@link WorkMode}
     */
    @NotNull
    public static WorkMode getWorkModeForAI(@Nullable final AbstractEntityAIInteract<?, ?> ai) {
        if (ai instanceof EntityAIStructureBuilder) {
            return AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.builderWorkMode.get();
        }
        if (ai instanceof EntityAIStructureMiner || ai instanceof EntityAIQuarrier) {
            return AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.minerWorkMode.get();
        }
        return WorkMode.NORMAL;
    }

    /**
     * Checks whether a Builder may accept work orders regardless of the 100-block distance limit
     * and start working on a new work order without walking back to their Builder's Hut first.
     *
     * @return {@code true} if remote order acceptance is enabled
     */
    public static boolean canBuilderAcceptOrdersAnywhere() {
        return AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.builderAcceptOrdersAnywhere.get()
                || AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.builderWorkMode.get() == WorkMode.GOD;
    }

    /**
     * Evaluates custom site positioning for {@code walkToConstructionSite(BlockPos)} when a non-default
     * {@link WorkMode} is configured.
     *
     * @param ai           the structure worker AI
     * @param worker       the citizen entity
     * @param building     the structure builder building
     * @param currentBlock the target block being placed or broken
     * @return {@code Boolean.TRUE} if the worker is in position to act, {@code Boolean.FALSE} if still walking,
     *         or {@code null} if {@link WorkMode#NORMAL} is active and vanilla/safety positioning should run
     */
    @Nullable
    public static Boolean evaluateWalkToConstructionSite(
            @NotNull final AbstractEntityAIInteract<?, ?> ai,
            @Nullable final AbstractEntityCitizen worker,
            @Nullable final AbstractBuildingStructureBuilder building,
            @Nullable final BlockPos currentBlock) {

        if (worker == null || building == null || currentBlock == null) {
            return null;
        }

        final WorkMode mode = getWorkModeForAI(ai);
        switch (mode) {
            case GOD:
                return Boolean.TRUE;

            case FORMALIST:
                return EntityNavigationUtils.walkToBuilding(worker, building);

            case SENTRY: {
                final IBuilderWorkOrder wo = building.getWorkOrder();
                final BlockPos anchor = wo != null && wo.getLocation() != null ? wo.getLocation() : currentBlock;
                if (BlockPosUtil.getDistance(worker.blockPosition(), anchor) <= SENTRY_ANCHOR_RADIUS + 2) {
                    worker.getNavigation().stop();
                    return Boolean.TRUE;
                }
                return EntityNavigationUtils.walkToPos(worker, anchor, SENTRY_ANCHOR_RADIUS, true);
            }

            case EXTENDED_REACH: {
                final int reach = AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.extendedReachRange.get();
                if (BlockPosUtil.getDistance(worker.blockPosition(), currentBlock) <= reach) {
                    return Boolean.TRUE;
                }
                final int walkTargetRange = Math.max(4, reach - 2);
                return EntityNavigationUtils.walkToPos(worker, currentBlock, walkTargetRange, true);
            }

            case NORMAL:
            default:
                return null;
        }
    }

    /**
     * Determines whether {@code AbstractEntityAIInteract.checkMiningLocation} should bypass its strict
     * {@code walkWithProxy(safeStand)} proximity check under the active {@link WorkMode}.
     *
     * @param ai          the worker AI instance
     * @param worker      the citizen entity
     * @param blockToMine the block being mined
     * @param safeStand   the stand position requested by the caller
     * @return {@code true} if the worker is already within valid reach for their configured {@link WorkMode}
     */
    public static boolean shouldBypassMiningStandWalk(
            @NotNull final AbstractEntityAIInteract<?, ?> ai,
            @Nullable final AbstractEntityCitizen worker,
            @NotNull final BlockPos blockToMine,
            @Nullable final BlockPos safeStand) {

        if (worker == null) {
            return false;
        }

        final WorkMode mode = getWorkModeForAI(ai);
        switch (mode) {
            case GOD:
            case FORMALIST:
            case SENTRY:
                return true;

            case EXTENDED_REACH: {
                final int reach = AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.extendedReachRange.get();
                final BlockPos reference = safeStand != null ? safeStand : blockToMine;
                return BlockPosUtil.getDistance(worker.blockPosition(), blockToMine) <= reach
                        || BlockPosUtil.getDistance(worker.blockPosition(), reference) <= reach;
            }

            case NORMAL:
            default:
                return false;
        }
    }
}
