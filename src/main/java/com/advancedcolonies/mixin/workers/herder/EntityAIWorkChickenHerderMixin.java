package com.advancedcolonies.mixin.workers.herder;

import com.advancedcolonies.workers.herder.HerdingAndButcherHelper;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingChickenHerder;
import com.minecolonies.core.colony.jobs.JobChickenHerder;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkChickenHerder;
import net.minecraft.world.entity.animal.Animal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin Mixin hook on {@link EntityAIWorkChickenHerder} bypassing the random miss check
 * and delegating to {@link AbstractEntityAIHerder#butcherAnimal(Animal)} when instant
 * butcher kill is enabled.
 */
@Mixin(value = EntityAIWorkChickenHerder.class, remap = false)
public abstract class EntityAIWorkChickenHerderMixin extends AbstractEntityAIHerder<JobChickenHerder, BuildingChickenHerder> {

    protected EntityAIWorkChickenHerderMixin(@NotNull final JobChickenHerder job) {
        super(job);
    }

    @Inject(method = "butcherAnimal", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onButcherChicken(
            @Nullable final Animal animal,
            final CallbackInfo ci) {
        if (HerdingAndButcherHelper.isInstantButcherKillEnabled()) {
            super.butcherAnimal(animal);
            ci.cancel();
        }
    }
}
