package com.advancedcolonies.mixin.workers.herder;

import com.advancedcolonies.workers.herder.HerdingAndButcherHelper;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCowboy;
import com.minecolonies.core.colony.jobs.JobCowboy;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkCowboy;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.COWBOY_MILK;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.COWBOY_STEW;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.INVENTORY_FULL;
import static com.minecolonies.api.util.constant.StatisticsConstants.MILKING_ATTEMPTS;

/**
 * Thin Mixin on {@link EntityAIWorkCowboy} supporting server-limited multi-milking
 * and multi-stewing per visit via {@link HerdingAndButcherHelper}.
 */
@Mixin(value = EntityAIWorkCowboy.class, remap = false)
public abstract class EntityAIWorkCowboyMixin extends AbstractEntityAIHerder<JobCowboy, BuildingCowboy> {

    @Shadow
    @Final
    private static VisibleCitizenStatus HERD_COW;

    @Shadow
    @Final
    private static int MILK_COOL_DOWN;

    @Shadow
    private int milkCoolDown;

    @Shadow
    private int stewCoolDown;

    @Unique
    private int advancedColonies$milkActionsThisVisit = 0;

    @Unique
    private int advancedColonies$stewActionsThisVisit = 0;

    protected EntityAIWorkCowboyMixin(@NotNull final JobCowboy job) {
        super(job);
    }

    @Inject(method = "milkCows", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onMilkCows(final CallbackInfoReturnable<IAIState> cir) {
        if (!HerdingAndButcherHelper.isCowboyMultiMilkingEnabled() || building == null) {
            return;
        }

        worker.getCitizenData().setVisibleStatus(HERD_COW);

        if (!worker.getCitizenInventoryHandler().hasItemInInventory(building.getMilkInputItem().getItem())) {
            if (InventoryUtils.hasBuildingEnoughElseCount(building, new ItemStorage(building.getMilkInputItem()), 1) > 0
                    && walkToBuilding()) {
                checkAndTransferFromHut(building.getMilkInputItem());
            } else {
                milkCoolDown = MILK_COOL_DOWN;
                cir.setReturnValue(DECIDE);
                return;
            }
        }

        final Cow cow = searchForAnimals(a -> a instanceof Cow && !(a instanceof MushroomCow) && !a.isBaby())
                .stream()
                .map(a -> (Cow) a)
                .findFirst()
                .orElse(null);

        if (cow == null) {
            milkCoolDown = MILK_COOL_DOWN;
            cir.setReturnValue(DECIDE);
            return;
        }

        walkingToAnimal(cow);

        if (equipItem(InteractionHand.MAIN_HAND,
                Collections.singletonList(new ItemStorage(building.getMilkInputItem().getItem(), building.getMilkInputItem().getCount())))) {
            incrementActionsDoneAndDecSaturation();
            StatsUtil.trackStat(building, MILKING_ATTEMPTS, 1);
            worker.getCitizenExperienceHandler().addExperience(1.0D);

            final BuildingCowboy.HerdingModule herdingModule = building.getFirstModuleOccurance(BuildingCowboy.HerdingModule.class);
            if (InventoryUtils.addItemStackToItemHandler(worker.getInventoryCitizen(), building.getMilkOutputItem())) {
                if (herdingModule != null) {
                    herdingModule.onMilked();
                }
                CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, getItemSlot(building.getMilkOutputItem().getItem()));
                InventoryUtils.tryRemoveStackFromItemHandler(worker.getInventoryCitizen(), building.getMilkInputItem());
            } else {
                advancedColonies$milkActionsThisVisit = 0;
                cir.setReturnValue(INVENTORY_FULL);
                return;
            }

            final int maxActions = HerdingAndButcherHelper.computeMaxMilkActionsPerVisit(getPrimarySkillLevel());
            if (++advancedColonies$milkActionsThisVisit >= maxActions
                    || herdingModule == null
                    || !herdingModule.canTryToMilk()) {
                advancedColonies$milkActionsThisVisit = 0;
                cir.setReturnValue(INVENTORY_FULL);
                return;
            }

            setDelay(10);
            cir.setReturnValue(COWBOY_MILK);
            return;
        }

        cir.setReturnValue(DECIDE);
    }

    @Inject(method = "milkMooshrooms", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onMilkMooshrooms(final CallbackInfoReturnable<IAIState> cir) {
        if (!HerdingAndButcherHelper.isCowboyMultiMilkingEnabled() || building == null) {
            return;
        }

        worker.getCitizenData().setVisibleStatus(HERD_COW);

        if (!worker.getCitizenInventoryHandler().hasItemInInventory(Items.BOWL)) {
            if (InventoryUtils.hasBuildingEnoughElseCount(building, new ItemStorage(new ItemStack(Items.BOWL, 1)), 1) > 0
                    && walkToBuilding()) {
                checkAndTransferFromHut(new ItemStack(Items.BOWL, 1));
            } else {
                stewCoolDown = MILK_COOL_DOWN;
                cir.setReturnValue(DECIDE);
                return;
            }
        }

        final MushroomCow mooshroom = searchForAnimals(a -> a instanceof MushroomCow && !a.isBaby())
                .stream()
                .map(a -> (MushroomCow) a)
                .findFirst()
                .orElse(null);

        if (mooshroom == null) {
            stewCoolDown = MILK_COOL_DOWN;
            cir.setReturnValue(DECIDE);
            return;
        }

        walkingToAnimal(mooshroom);

        if (equipItem(InteractionHand.MAIN_HAND, Collections.singletonList(new ItemStorage(Items.BOWL)))) {
            final FakePlayer fakePlayer = FakePlayerFactory.getMinecraft((ServerLevel) worker.level());
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL));
            incrementActionsDoneAndDecSaturation();
            StatsUtil.trackStat(building, MILKING_ATTEMPTS, 1);
            worker.getCitizenExperienceHandler().addExperience(1.0D);

            final BuildingCowboy.HerdingModule herdingModule = building.getFirstModuleOccurance(BuildingCowboy.HerdingModule.class);
            if (mooshroom.mobInteract(fakePlayer, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME) {
                if (InventoryUtils.addItemStackToItemHandler(worker.getInventoryCitizen(), fakePlayer.getMainHandItem())) {
                    if (herdingModule != null) {
                        herdingModule.onStewed();
                    }
                    CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, getItemSlot(fakePlayer.getMainHandItem().getItem()));
                    InventoryUtils.tryRemoveStackFromItemHandler(worker.getInventoryCitizen(), new ItemStack(Items.BOWL));
                } else {
                    advancedColonies$stewActionsThisVisit = 0;
                    cir.setReturnValue(INVENTORY_FULL);
                    return;
                }
            }

            final int maxActions = HerdingAndButcherHelper.computeMaxMilkActionsPerVisit(getPrimarySkillLevel());
            if (++advancedColonies$stewActionsThisVisit >= maxActions
                    || herdingModule == null
                    || !herdingModule.canTryToStew()) {
                advancedColonies$stewActionsThisVisit = 0;
                cir.setReturnValue(INVENTORY_FULL);
                return;
            }

            setDelay(10);
            cir.setReturnValue(COWBOY_STEW);
            return;
        }

        cir.setReturnValue(DECIDE);
    }
}
