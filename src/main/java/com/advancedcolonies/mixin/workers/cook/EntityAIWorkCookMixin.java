package com.advancedcolonies.mixin.workers.cook;

import com.advancedcolonies.workers.cook.CookServiceOptimizer;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.jobs.JobCook;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIUsesFurnace;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkCook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;
import java.util.function.Predicate;

/**
 * Thin Mixin hook on {@link EntityAIWorkCook} delegating out-of-order customer pool management,
 * stuck-customer bypass, SittingEntity floor-offset compensation, drive-by serving, unified
 * {@code canEat} checks, and smart inventory dump thresholds to {@link CookServiceOptimizer}.
 */
@Mixin(value = EntityAIWorkCook.class, remap = false)
public abstract class EntityAIWorkCookMixin extends AbstractEntityAIUsesFurnace<JobCook, BuildingCook> {

    @Shadow
    @Final
    private Queue<AbstractEntityCitizen> citizenToServe;

    @Shadow
    @Final
    private Queue<Player> playerToServe;

    @Unique
    @Nullable
    private AbstractEntityCitizen advancedcolonies$activeServedCustomer;

    protected EntityAIWorkCookMixin(@NotNull final JobCook job) {
        super(job);
    }

    @Inject(method = "canEat", at = @At("HEAD"), cancellable = true, remap = false)
    private void advancedColonies$onCanEat(
            final ItemStack stack,
            final AbstractEntityCitizen citizen,
            final CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(CookServiceOptimizer.canCitizenEatStack(stack, citizen));
    }

    @Inject(method = "checkForImportantJobs", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onCheckForImportantJobsReturn(final CallbackInfoReturnable<IAIState> cir) {
        final IAIState nextState = CookServiceOptimizer.onCheckForImportantJobsReturn(
                this.world,
                this.worker,
                this.building,
                this.citizenToServe,
                this.playerToServe,
                cir.getReturnValue());
        if (nextState != cir.getReturnValue()) {
            cir.setReturnValue(nextState);
        }
    }

    @Inject(method = "serveFoodToCitizen", at = @At("HEAD"))
    private void advancedColonies$onServeFoodToCitizenHead(final CallbackInfoReturnable<IAIState> cir) {
        this.advancedcolonies$activeServedCustomer = null;
        CookServiceOptimizer.onServeFoodToCitizenHead(
                this.world,
                this.worker,
                this.building,
                this.citizenToServe);
    }

    @Redirect(
            method = "serveFoodToCitizen",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/colony/buildings/workerbuildings/BuildingCook;isInBuilding(Lnet/minecraft/core/BlockPos;)Z"
            ),
            remap = false
    )
    private boolean advancedColonies$isSeatedCustomerInBuilding(
            final BuildingCook buildingCook,
            final BlockPos rawPos) {
        return CookServiceOptimizer.isCustomerInBuilding(buildingCook, this.citizenToServe.peek(), rawPos);
    }

    @Redirect(
            method = "serveFoodToCitizen",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/entity/ai/workers/service/EntityAIWorkCook;walkToWorkPos(Lnet/minecraft/core/BlockPos;)Z"
            ),
            remap = false
    )
    private boolean advancedColonies$walkToCustomerOrServeInRange(
            final EntityAIWorkCook self,
            final BlockPos targetPos) {
        final AbstractEntityCitizen peeked = this.citizenToServe.peek();
        final BlockPos effectivePos = CookServiceOptimizer.getEffectiveCustomerPos(peeked, targetPos);
        if (CookServiceOptimizer.isWithinImmediateServeRange(this.worker, effectivePos)) {
            if (this.citizenToServe.size() <= 1) {
                this.worker.getNavigation().stop();
            }
            this.advancedcolonies$activeServedCustomer = peeked;
            CookServiceOptimizer.onCustomerServed(this.worker, peeked);
            return true;
        }
        final boolean arrived = this.walkToWorkPos(effectivePos != null ? effectivePos : targetPos);
        if (arrived) {
            this.advancedcolonies$activeServedCustomer = peeked;
            CookServiceOptimizer.onCustomerServed(this.worker, peeked);
        }
        return arrived;
    }

    @Redirect(
            method = "serveFoodToCitizen",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/util/InventoryUtils;hasItemInItemHandler(Lnet/neoforged/neoforge/items/IItemHandler;Ljava/util/function/Predicate;)Z"
            ),
            remap = false
    )
    private boolean advancedColonies$hasValidMenuFoodInCustomerInventory(
            final IItemHandler citizenInventory,
            final Predicate<ItemStack> rawMenuPredicate) {
        return CookServiceOptimizer.hasValidMenuFoodInCustomerHandler(
                citizenInventory,
                rawMenuPredicate,
                this.building,
                this.advancedcolonies$activeServedCustomer);
    }

    @Inject(method = "getActionsDoneUntilDumping", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onGetActionsDoneUntilDumping(final CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CookServiceOptimizer.getSmartActionsUntilDumping(
                this.worker, this.citizenToServe));
    }
}
