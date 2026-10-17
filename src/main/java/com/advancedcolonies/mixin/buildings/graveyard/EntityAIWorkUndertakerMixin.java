package com.advancedcolonies.mixin.buildings.graveyard;

import com.advancedcolonies.buildings.graveyard.GraveyardResurrectionHelper;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.managers.interfaces.ICitizenManager;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGraveyard;
import com.minecolonies.core.colony.jobs.JobUndertaker;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkUndertaker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Thin Mixin hook on {@link EntityAIWorkUndertaker} delegating to {@link GraveyardResurrectionHelper}
 * so that when a fallen citizen is resurrected by the Undertaker, their grave items are returned
 * directly to their inventory instead of being dumped into the Graveyard rack.
 */
@Mixin(value = EntityAIWorkUndertaker.class, remap = false)
public abstract class EntityAIWorkUndertakerMixin extends AbstractEntityAIInteract<JobUndertaker, BuildingGraveyard> {

    @Shadow
    protected abstract int getShovelSlot();

    protected EntityAIWorkUndertakerMixin(@NotNull final JobUndertaker job) {
        super(job);
    }

    /**
     * Intercepts {@link ICitizenManager#resurrectCivilianData(CompoundTag, boolean, Level, BlockPos)}
     * inside {@code tryResurrect()} and immediately transfers the resurrected citizen's items from the
     * Undertaker's inventory back into the resurrected citizen's inventory.
     */
    @Redirect(
            method = "tryResurrect",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/colony/managers/interfaces/ICitizenManager;resurrectCivilianData(Lnet/minecraft/nbt/CompoundTag;ZLnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lcom/minecolonies/api/colony/ICitizenData;"
            )
    )
    private ICitizenData advancedcolonies$returnGearToResurrectedCitizen(
            final ICitizenManager citizenManager,
            final CompoundTag citizenDataNBT,
            final boolean resetId,
            final Level world,
            final BlockPos gravePos) {
        final ICitizenData resurrected = citizenManager.resurrectCivilianData(citizenDataNBT, resetId, world, gravePos);
        if (resurrected != null) {
            GraveyardResurrectionHelper.returnUndertakerGraveItemsToCitizen(
                    this.worker,
                    resurrected,
                    this.getShovelSlot());
        }
        return resurrected;
    }
}
