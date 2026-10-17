package com.advancedcolonies.mixin.common.interaction;

import com.advancedcolonies.common.interaction.CitizenInteractionHelper;
import com.minecolonies.core.colony.permissions.ColonyPermissionEventHandler;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Thin Mixin hook on {@link ColonyPermissionEventHandler} to optionally disable the
 * levitation punishment effect when players trigger colony permission denials.
 */
@Mixin(value = ColonyPermissionEventHandler.class, remap = false)
public abstract class ColonyPermissionEventHandlerMixin {

    /**
     * Suppresses the {@code MobEffects.LEVITATION} application in {@code cancelEvent} when
     * {@code disablePermissionLevitation} is enabled in the configuration.
     *
     * @param entity         the entity receiving the effect
     * @param effectInstance the levitation effect instance
     * @return {@code false} if suppressed, or the vanilla {@code addEffect} result
     */
    @Redirect(
            method = "cancelEvent(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/entity/Entity;Lcom/minecolonies/core/colony/Colony;Lcom/minecolonies/api/colony/permissions/Action;Lnet/minecraft/core/BlockPos;Ljava/util/function/Consumer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"
            )
    )
    private boolean advancedcolonies$suppressPermissionLevitation(
            final LivingEntity entity,
            final MobEffectInstance effectInstance) {
        if (CitizenInteractionHelper.shouldSuppressPermissionLevitation()) {
            return false;
        }
        return entity.addEffect(effectInstance);
    }
}
