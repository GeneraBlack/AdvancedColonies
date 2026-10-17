package com.advancedcolonies.mixin.common.interaction;

import com.advancedcolonies.common.health.CitizenHealthAndHospitalHelper;
import com.advancedcolonies.common.interaction.CitizenInteractionHelper;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityCitizen} for the Compass "Mind Reader" AI inspector
 * and colony damage alerts.
 */
@Mixin(value = EntityCitizen.class, remap = false)
public abstract class EntityCitizenMixin {

    /**
     * Intercepts right-click interactions with a Compass to display the citizen's live AI state and vitals.
     *
     * @param player the interacting player
     * @param hand   the interaction hand
     * @param cir    callback info returnable
     */
    @Inject(method = "directPlayerInteraction", at = @At("HEAD"), cancellable = true)
    private void advancedcolonies$onCompassMindReaderInteract(
            final Player player,
            final InteractionHand hand,
            final CallbackInfoReturnable<InteractionResult> cir) {
        final EntityCitizen self = (EntityCitizen) (Object) this;
        final InteractionResult result = CitizenInteractionHelper.tryInspectCitizenWithCompass(self, player, hand);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    /**
     * Sends a colony manager alert when a non-guard citizen takes damage and {@code alertOnCitizenDamage} is enabled.
     *
     * @param damageSource the damage source
     * @param damage       the damage dealt
     * @param sourceEntity the attacking entity, if any
     * @param cir          callback info returnable
     */
    @Inject(method = "handleDamagePerformed", at = @At("RETURN"))
    private void advancedcolonies$onCitizenDamagePerformed(
            final DamageSource damageSource,
            final float damage,
            final Entity sourceEntity,
            final CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            final EntityCitizen self = (EntityCitizen) (Object) this;
            CitizenHealthAndHospitalHelper.notifyColonyOnCitizenDamage(self, damageSource, damage);
        }
    }
}
