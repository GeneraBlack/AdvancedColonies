package com.advancedcolonies.workers.herder;

/**
 * Duck-interface implemented by {@link com.advancedcolonies.mixin.common.bee.BeeMixin}
 * to expose package-private {@link net.minecraft.world.entity.animal.Bee} mutators
 * without full entity NBT serialization.
 */
public interface IBeeAccessor {

    /**
     * Directly sets whether the bee is carrying nectar (`Bee#setHasNectar(boolean)`).
     */
    void advancedcolonies$setHasNectar(boolean hasNectar);
}
