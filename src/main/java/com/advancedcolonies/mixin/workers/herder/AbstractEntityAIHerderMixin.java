package com.advancedcolonies.mixin.workers.herder;

import com.advancedcolonies.workers.herder.HerdingAndButcherHelper;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Thin Mixin hook on {@link AbstractEntityAIHerder} delegating exponential animal cap scaling,
 * single-strike butchering, and pen item collection to {@link HerdingAndButcherHelper}.
 */
@Mixin(value = AbstractEntityAIHerder.class, remap = false)
public abstract class AbstractEntityAIHerderMixin<J extends AbstractJob<?, J>, B extends AbstractBuilding>
        extends AbstractEntityAIInteract<J, B> {

    protected AbstractEntityAIHerderMixin(@NotNull final J job) {
        super(job);
    }

    @Shadow
    public abstract int getMaxAnimalMultiplier();

    @Shadow
    public abstract double getButcheringAttackDamage();

    @Shadow
    public abstract List<? extends ItemEntity> searchForItemsInArea();

    @Inject(method = "chanceToButcher", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onChanceToButcher(
            final List<? extends Animal> allAnimals,
            final CallbackInfoReturnable<Double> cir) {
        final Double customChance = HerdingAndButcherHelper.evaluateChanceToButcher(
                building, allAnimals, getMaxAnimalMultiplier());
        if (customChance != null) {
            cir.setReturnValue(customChance);
        }
    }

    @Inject(method = "butcherSwing", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onButcherSwing(
            final FakePlayer fakePlayer,
            final Animal animal,
            final CallbackInfo ci) {
        if (!HerdingAndButcherHelper.isInstantButcherKillEnabled() || fakePlayer == null || animal == null) {
            return;
        }
        worker.swing(InteractionHand.MAIN_HAND);
        final DamageSource ds = animal.level().damageSources().playerAttack(fakePlayer);
        animal.hurt(ds, HerdingAndButcherHelper.computeButcherDamage(animal, getButcheringAttackDamage()));
        CitizenItemUtils.damageItemInHand(worker, InteractionHand.MAIN_HAND, 1);
        ci.cancel();
    }

    @Inject(method = "pickupItems", at = @At("HEAD"))
    private void advancedColonies$onPickupItems(final CallbackInfoReturnable<IAIState> cir) {
        HerdingAndButcherHelper.collectPenItems(worker, searchForItemsInArea());
    }
}
