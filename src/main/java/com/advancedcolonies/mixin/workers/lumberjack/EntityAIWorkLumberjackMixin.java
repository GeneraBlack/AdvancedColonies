package com.advancedcolonies.mixin.workers.lumberjack;

import com.advancedcolonies.workers.lumberjack.LumberjackForestryHelper;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingLumberjack;
import com.minecolonies.core.colony.jobs.JobLumberjack;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.ai.workers.production.EntityAIWorkLumberjack;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIWorkLumberjack} delegating canopy drop teleportation
 * and sapling-free replanting to {@link LumberjackForestryHelper}.
 */
@Mixin(value = EntityAIWorkLumberjack.class, remap = false)
public abstract class EntityAIWorkLumberjackMixin extends AbstractEntityAICrafting<JobLumberjack, BuildingLumberjack> {

    @Shadow
    private int timeWaited;

    @Shadow
    protected abstract int findSaplingSlot();

    protected EntityAIWorkLumberjackMixin(@NotNull final JobLumberjack job) {
        super(job);
    }

    @Inject(method = "fillItemsList", at = @At("HEAD"))
    private void advancedColonies$onFillItemsList(final CallbackInfo ci) {
        LumberjackForestryHelper.teleportCanopyDropsToLumberjack(world, worker, job);
    }

    @Inject(method = "plantSapling(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onPlantSapling(
            @NotNull final BlockPos location,
            final CallbackInfoReturnable<Boolean> cir) {
        if (findSaplingSlot() == -1
                && LumberjackForestryHelper.tryReplantWithoutSaplings(world, worker, building, job, location)) {
            timeWaited = 0;
            incrementActionsDoneAndDecSaturation();
            setDelay(10);
            cir.setReturnValue(true);
        }
    }
}
