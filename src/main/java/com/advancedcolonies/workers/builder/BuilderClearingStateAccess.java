package com.advancedcolonies.workers.builder;

/**
 * Duck-interface implemented by {@code AbstractEntityAIStructureMixin} to track whether
 * the current structure build is executing its initial bottom-up clearing pass without
 * adding custom enum constants to {@code BuildingProgressStage} (preserving 100% vanilla NBT compatibility).
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
}
