package com.advancedcolonies.workers.builder;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Duck-interface implemented by {@code AbstractEntityAIStructureMixin} to track whether
 * the current structure build is executing its initial bottom-up clearing pass and to expose
 * live structure construction telemetry and unstuck reset hooks for the Visual Debugger.
 */
public interface BuilderClearingStateAccess {

    /**
     * @return {@code true} if the structure AI is currently in the initial bottom-up clearing pass.
     */
    boolean advancedColonies$isInBottomUpClearPass();

    /**
     * Sets whether the structure AI is currently in the initial bottom-up clearing pass.
     *
     * @param active {@code true} to activate bottom-up clearing, {@code false} once completed.
     */
    void advancedColonies$setInBottomUpClearPass(boolean active);

    /**
     * @return the active target block in world coordinates ({@code blockToMine} or {@code getPosToWorkAt()}), or {@code null}.
     */
    @Nullable
    default BlockPos advancedColonies$getCurrentWorkTargetPos() {
        return null;
    }

    /**
     * @return the current {@code workFrom} stand position, or {@code null}.
     */
    @Nullable
    default BlockPos advancedColonies$getWorkFromPos() {
        return null;
    }

    /**
     * @return a human-readable description of the active {@code BuildingProgressStage}, or empty string if none.
     */
    @NotNull
    default String advancedColonies$getStageDescription() {
        return "";
    }

    /**
     * Resets cached standing positions and path handles when a manual unstuck is triggered.
     */
    default void advancedColonies$resetStuckBuildState() {
    }
}
