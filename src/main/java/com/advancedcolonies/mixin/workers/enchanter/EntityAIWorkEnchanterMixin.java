package com.advancedcolonies.mixin.workers.enchanter;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.workers.enchanter.EnchanterWorkHelper;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.buildings.modules.EnchanterStationsModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingEnchanter;
import com.minecolonies.core.colony.jobs.JobEnchanter;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkEnchanter;
import com.minecolonies.core.network.messages.client.CircleParticleEffectMessage;
import com.minecolonies.core.network.messages.client.StreamParticleEffectMessage;
import com.minecolonies.core.util.WorkerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.ENCHANT;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.ENCHANTER_DRAIN;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.GATHERING_REQUIRED_MATERIALS;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;

/**
 * Thin Mixin on {@link EntityAIWorkEnchanter} delegating batch book/tome requests,
 * server-limited Mana skill caps, and autonomous draining to {@link EnchanterWorkHelper}.
 */
@Mixin(value = EntityAIWorkEnchanter.class, remap = false)
public abstract class EntityAIWorkEnchanterMixin extends AbstractEntityAICrafting<JobEnchanter, BuildingEnchanter> {

    @Shadow
    @Final
    private static int MANA_REQ_PER_LEVEL;

    @Shadow
    @Final
    private static Predicate<ItemStack> IS_BOOK;

    @Shadow
    @Final
    private static Predicate<ItemStack> IS_ANCIENT_TOME;

    @Shadow
    @Final
    private static long MIN_DISTANCE_TO_DRAIN;

    @Shadow
    @Final
    private static int MAX_PROGRESS_TICKS;

    @Shadow
    @Final
    private static double XP_PER_DRAIN;

    @Shadow
    private int progressTicks;

    @Shadow
    private ICitizenData citizenToGatherFrom;

    @Shadow
    protected abstract void resetDraining();

    @Unique
    private boolean advancedColonies$shouldGainMoreMana = false;

    @Unique
    private boolean advancedColonies$gatherWithoutCitizen = false;

    protected EntityAIWorkEnchanterMixin(@NotNull final JobEnchanter job) {
        super(job);
    }

    @Inject(method = "decide", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onDecide(final CallbackInfoReturnable<IAIState> cir) {
        if (!EnchanterWorkHelper.isEnhancedEnchanterEnabled() || building == null) {
            return;
        }

        worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (!walkToBuilding()) {
            cir.setReturnValue(START_WORKING);
            return;
        }

        final IAIState craftState = getNextCraftingState();
        if (craftState != IDLE) {
            cir.setReturnValue(craftState);
            return;
        }

        if (wantInventoryDumped()) {
            cir.setReturnValue(getState());
            return;
        }

        final int batchSize = EnchanterWorkHelper.getClampedBookBatchSize();
        if (EnchanterWorkHelper.shouldGatherMana(
                getPrimarySkillLevel(),
                MANA_REQ_PER_LEVEL,
                building,
                advancedColonies$shouldGainMoreMana)) {

            final int booksInInv = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), IS_BOOK);
            if (booksInInv <= 0) {
                final int booksInBuilding = InventoryUtils.hasBuildingEnoughElseCount(building, IS_BOOK, 1);
                if (booksInBuilding > 0) {
                    needsCurrently = new Tuple<>(IS_BOOK, 1);
                    cir.setReturnValue(GATHERING_REQUIRED_MATERIALS);
                    return;
                }
                checkIfRequestForItemExistOrCreateAsync(new ItemStack(Items.BOOK, 1), batchSize, 1, false);
            }

            final BlockPos posToDrainFrom = EnchanterWorkHelper.selectBuildingToDrainFrom(worker, building);
            if (posToDrainFrom == null) {
                cir.setReturnValue(IDLE);
                return;
            }

            job.setBuildingToDrainFrom(posToDrainFrom);
            advancedColonies$shouldGainMoreMana = false;
            cir.setReturnValue(ENCHANTER_DRAIN);
            return;
        }

        if (!EnchanterWorkHelper.isAncientTomeCraftingDisabled(building)) {
            final int tomesInInv = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), IS_ANCIENT_TOME);
            if (tomesInInv <= 0) {
                final int tomesInBuilding = InventoryUtils.hasBuildingEnoughElseCount(building, IS_ANCIENT_TOME, 1);
                if (tomesInBuilding > 0) {
                    needsCurrently = new Tuple<>(IS_ANCIENT_TOME, 1);
                    cir.setReturnValue(GATHERING_REQUIRED_MATERIALS);
                    return;
                }
                checkIfRequestForItemExistOrCreateAsync(new ItemStack(ModItems.ancientTome, 1), batchSize, 1, false);
                advancedColonies$shouldGainMoreMana = true;
                cir.setReturnValue(IDLE);
                return;
            }
        }

        cir.setReturnValue(ENCHANT);
    }

    @Inject(method = "gatherAndDrain", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onGatherAndDrain(final CallbackInfoReturnable<IAIState> cir) {
        if (!EnchanterWorkHelper.isEnhancedEnchanterEnabled() || building == null) {
            return;
        }

        if (job.getPosToDrainFrom() == null) {
            cir.setReturnValue(IDLE);
            return;
        }

        final IBuilding buildingWorker = building.getColony().getServerBuildingManager().getBuilding(job.getPosToDrainFrom());
        if (!walkToBuilding(buildingWorker)) {
            cir.setReturnValue(getState());
            return;
        }

        if (buildingWorker == null) {
            resetDraining();
            final EnchanterStationsModule module = building.getFirstModuleOccurance(EnchanterStationsModule.class);
            if (module != null) {
                module.removeWorker(job.getPosToDrainFrom());
            }
            cir.setReturnValue(IDLE);
            return;
        }

        if (citizenToGatherFrom == null) {
            final List<AbstractEntityCitizen> workers = new ArrayList<>();
            if (getModuleForJob() != null && getModuleForJob().getAssignedEntities() != null) {
                for (final Optional<AbstractEntityCitizen> citizen : Objects.requireNonNull(getModuleForJob().getAssignedEntities())) {
                    citizen.ifPresent(workers::add);
                }
            }

            if (workers.isEmpty()) {
                resetDraining();
                cir.setReturnValue(START_WORKING);
                return;
            }

            final AbstractEntityCitizen selected = workers.size() > 1
                    ? workers.get(worker.getRandom().nextInt(workers.size()))
                    : workers.getFirst();
            citizenToGatherFrom = selected.getCitizenData();
            progressTicks = 0;
            cir.setReturnValue(getState());
            return;
        }

        if (citizenToGatherFrom.getEntity().isEmpty()) {
            citizenToGatherFrom = null;
            cir.setReturnValue(getState());
            return;
        }

        if (progressTicks == 0) {
            advancedColonies$gatherWithoutCitizen = false;
            if (BlockPosUtil.getDistance2D(citizenToGatherFrom.getEntity().get().blockPosition(), worker.blockPosition()) > MIN_DISTANCE_TO_DRAIN) {
                if (job.incrementWaitingTicks()) {
                    cir.setReturnValue(getState());
                    return;
                }
                if (AdvancedColoniesConfig.FURNACE_AND_CRAFT.allowEnchanterAutonomousDrain.get()) {
                    advancedColonies$gatherWithoutCitizen = true;
                } else {
                    resetDraining();
                    cir.setReturnValue(IDLE);
                    return;
                }
            }
        }

        progressTicks++;
        if (progressTicks < MAX_PROGRESS_TICKS) {
            final Vec3 start = worker.position().add(0, 2, 0);
            final Vec3 goal = advancedColonies$gatherWithoutCitizen
                    ? job.getPosToDrainFrom().getCenter().add(0, 2, 0)
                    : citizenToGatherFrom.getEntity().get().position().add(0, 2, 0);

            new StreamParticleEffectMessage(start, goal, ParticleTypes.ENCHANT, progressTicks % MAX_PROGRESS_TICKS, MAX_PROGRESS_TICKS)
                    .sendToTrackingEntity(worker);
            new CircleParticleEffectMessage(start, ParticleTypes.HAPPY_VILLAGER, progressTicks)
                    .sendToTrackingEntity(worker);

            WorkerUtil.faceBlock(BlockPos.containing(goal), worker);
            worker.swing(worker.getRandom().nextBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            cir.setReturnValue(getState());
            return;
        }

        EnchanterWorkHelper.completeDrainCycle(
                world,
                worker,
                building,
                job,
                citizenToGatherFrom,
                getSecondarySkillLevel(),
                advancedColonies$gatherWithoutCitizen,
                XP_PER_DRAIN);
        resetDraining();
        cir.setReturnValue(IDLE);
    }
}
