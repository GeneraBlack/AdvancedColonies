package com.advancedcolonies.workers.builder;

import com.advancedcolonies.buildings.builder.BuilderClearingSettingHelper;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.ldtteam.structurize.placement.BlockPlacementResult;
import com.ldtteam.structurize.placement.StructurePhasePlacementResult;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.BlueprintPositionInfo;
import net.neoforged.neoforge.common.util.TriPredicate;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Encapsulates the Bottom-Up Clearing execution logic for Builders.
 * <p>
 * Instead of injecting a non-vanilla enum value into {@link BuildingProgressStage} (which would break
 * world saves if the mod is ever removed), this helper keeps the stage as {@link BuildingProgressStage#CLEAR}
 * and runs two passes when Bottom-Up Clearing is enabled:
 * <ol>
 *   <li><b>Pass 1 (Bottom-Up):</b> Increments the structure iterator from bottom to top, removing obstacles.</li>
 *   <li><b>Pass 2 (Top-Down Cleanup):</b> Decrements the iterator from top to bottom to catch any gravity blocks
 *       (sand, gravel) that fell during Pass 1.</li>
 * </ol>
 */
public final class BuilderClearingHelper {

    private BuilderClearingHelper() {
        // Utility class
    }

    /**
     * Initializes the bottom-up clearing pass state when a new structure is loaded for construction.
     *
     * @param structureAI the structure AI instance.
     * @param building    the worker's building.
     * @param stages      the stages configured for the new {@code BuildingStructureHandler}.
     */
    public static void onStructureLoaded(
            @NotNull final AbstractEntityAIStructure<?, ?> structureAI,
            @NotNull final AbstractBuildingStructureBuilder building,
            @NotNull final BuildingProgressStage[] stages) {

        final boolean startsWithClear = stages.length > 0 && stages[0] == BuildingProgressStage.CLEAR;
        final boolean isFreshClear = building.getProgress() == null
                || building.getProgress().getB() == null
                || building.getProgress().getB() == BuildingProgressStage.CLEAR;

        final boolean enableBottomUp = startsWithClear
                && isFreshClear
                && building instanceof BuildingBuilder
                && BuilderClearingSettingHelper.shouldClearFromBottomUp(building);

        if (structureAI instanceof BuilderClearingStateAccess access) {
            access.advancedColonies$setInBottomUpClearPass(enableBottomUp);
        }
    }

    /**
     * Executes a single bottom-up clearing step when the bottom-up pass is active.
     *
     * @param structureAI      the structure AI instance.
     * @param world            the level.
     * @param placer           the active {@link StructurePlacer}.
     * @param progress         the current iterator progress position, or {@code null}.
     * @param skipClearingPred the predicate deciding which positions do not need clearing.
     * @return the placement result if bottom-up clearing handled the step, or {@code null} to use default top-down clearing.
     */
    @Nullable
    public static StructurePhasePlacementResult tryExecuteBottomUpClearStep(
            @NotNull final AbstractEntityAIStructure<?, ?> structureAI,
            @NotNull final Level world,
            @NotNull final StructurePlacer placer,
            @Nullable final BlockPos progress,
            @NotNull final TriPredicate<BlueprintPositionInfo, BlockPos, IStructureHandler> skipClearingPred) {

        if (!(structureAI instanceof BuilderClearingStateAccess access) || !access.advancedColonies$isInBottomUpClearPass()) {
            return null;
        }

        return placer.executeStructureStep(
                world,
                null,
                progress,
                StructurePlacer.Operation.BLOCK_REMOVAL,
                () -> placer.getIterator().increment(skipClearingPred::test),
                false
        );
    }

    /**
     * Intercepts the completion of the initial bottom-up clearing pass so the builder resets
     * the iterator and performs a standard top-down cleanup pass before advancing to {@code BUILD_SOLID}.
     *
     * @param structureAI  the structure AI instance.
     * @param currentStage the current building progress stage.
     * @param result       the placement result of the step.
     * @return {@code true} if the bottom-up pass just finished and transitioned into the top-down cleanup pass.
     */
    public static boolean tryTransitionFromBottomUpToTopDownClear(
            @NotNull final AbstractEntityAIStructure<?, ?> structureAI,
            @Nullable final BuildingProgressStage currentStage,
            @NotNull final StructurePhasePlacementResult result) {

        if (currentStage != BuildingProgressStage.CLEAR
                || result.getBlockResult().getResult() != BlockPlacementResult.Result.FINISHED) {
            return false;
        }

        if (structureAI instanceof BuilderClearingStateAccess access && access.advancedColonies$isInBottomUpClearPass()) {
            access.advancedColonies$setInBottomUpClearPass(false);
            return true;
        }
        return false;
    }

    /**
     * Determines whether a block should be skipped during clearing because it already matches
     * the target state in the blueprint.
     *
     * @param info    blueprint information at the current position.
     * @param pos     world position being checked.
     * @param handler structure handler instance.
     * @return {@code true} if the world block already matches the blueprint block and config allows skipping it.
     */
    public static boolean doesBlockAlreadyMatchBlueprint(
            @NotNull final BlueprintPositionInfo info,
            @NotNull final BlockPos pos,
            @NotNull final IStructureHandler handler) {

        if (!AdvancedColoniesConfig.BUILDER_AND_EXCAVATION.skipClearingMatchingBlueprintBlocks.get()) {
            return false;
        }
        return IPlacementHandler.doesWorldStateMatchBlueprintState(info.getBlockInfo(), pos, handler);
    }
}
