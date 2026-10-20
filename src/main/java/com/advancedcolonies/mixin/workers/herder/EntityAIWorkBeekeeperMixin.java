package com.advancedcolonies.mixin.workers.herder;

import com.advancedcolonies.workers.herder.BeeAIHelper;
import com.advancedcolonies.workers.herder.HerdingAndButcherHelper;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.jobs.JobBeekeeper;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkBeekeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Bee;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.BEEKEEPER_HARVEST;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.HERDER_BREED;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;
import static com.minecolonies.api.util.constant.BuildingConstants.BUILDING_FLOWER_LIST;
import static com.minecolonies.api.util.constant.TranslationConstants.COM_MINECOLONIES_COREMOD_BEEKEEPER_NOFLOWERS;
import static com.minecolonies.api.util.constant.TranslationConstants.NO_BEES;
import static com.minecolonies.api.util.constant.TranslationConstants.NO_HIVES;
import static com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkBeekeeper.searchForAnimals;

/**
 * Thin Mixin on {@link EntityAIWorkBeekeeper} prioritizing ready beehive harvesting
 * before breeding when {@code enableBeekeeperHarvestPriority} is enabled.
 */
@Mixin(value = EntityAIWorkBeekeeper.class, remap = false)
public abstract class EntityAIWorkBeekeeperMixin extends AbstractEntityAIInteract<JobBeekeeper, BuildingBeekeeper> {

    @Shadow
    @Final
    private static int DECIDING_DELAY;

    @Shadow
    @Final
    private static int NO_ANIMALS_DELAY;

    @Shadow
    @Final
    private static int NO_HIVES_DELAY;

    @Shadow
    @Final
    private static int NO_FLOWERS_DELAY;

    @Shadow
    protected abstract BlockPos getHiveToHarvest();

    @Shadow
    protected abstract int getBeesInHives();

    @Shadow
    protected abstract boolean isReadyForBreeding();

    protected EntityAIWorkBeekeeperMixin(@NotNull final JobBeekeeper job) {
        super(job);
    }

    @Inject(method = "decideWhatToDo", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onDecideWhatToDo(final CallbackInfoReturnable<IAIState> cir) {
        if (!HerdingAndButcherHelper.isBeekeeperHarvestPriorityEnabled() || building == null) {
            return;
        }

        setDelay(DECIDING_DELAY + (99 / Math.max(1, getSecondarySkillLevel()) - 1));

        final Set<BlockPos> hives = building.getHives();
        if (hives.isEmpty()) {
            worker.getCitizenData().triggerInteraction(
                    new StandardInteraction(Component.translatable(NO_HIVES), ChatPriority.BLOCKING));
            setDelay(NO_HIVES_DELAY);
            cir.setReturnValue(DECIDE);
            return;
        }

        final ItemListModule flowersModule = building.getModuleMatching(
                ItemListModule.class,
                m -> m.getId().equals(BUILDING_FLOWER_LIST));
        if (flowersModule != null
                && flowersModule.getList().isEmpty()
                && building.getSetting(BuildingBeekeeper.BREEDING).getValue()) {
            worker.getCitizenData().triggerInteraction(
                    new StandardInteraction(Component.translatable(COM_MINECOLONIES_COREMOD_BEEKEEPER_NOFLOWERS), ChatPriority.BLOCKING));
            setDelay(NO_FLOWERS_DELAY);
            cir.setReturnValue(DECIDE);
            return;
        }

        // Prioritize harvesting ready beehives before checking bee breeding
        final BlockPos hive = getHiveToHarvest();
        if (hive != null) {
            cir.setReturnValue(BEEKEEPER_HARVEST);
            return;
        }

        final List<Bee> bees = searchForAnimals(world, building);
        BeeAIHelper.bindColonyBeesToHives(world, hives, bees);

        final JobBeekeeper beekeeperJob = worker.getCitizenJobHandler().getColonyJob(JobBeekeeper.class);
        if (beekeeperJob != null) {
            if (bees.isEmpty()) {
                if (getBeesInHives() <= 0) {
                    beekeeperJob.tickNoBees();
                    if (beekeeperJob.checkForBeeInteraction()) {
                        worker.getCitizenData().triggerInteraction(
                                new StandardInteraction(Component.translatable(NO_BEES), ChatPriority.BLOCKING));
                    }
                } else {
                    beekeeperJob.resetCounter();
                }
                setDelay(NO_ANIMALS_DELAY);
                cir.setReturnValue(DECIDE);
                return;
            }
            beekeeperJob.resetCounter();
        }

        if (isReadyForBreeding()) {
            cir.setReturnValue(HERDER_BREED);
            return;
        }

        cir.setReturnValue(START_WORKING);
    }
}
