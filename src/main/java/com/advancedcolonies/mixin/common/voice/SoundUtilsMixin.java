package com.advancedcolonies.mixin.common.voice;

import com.advancedcolonies.common.voice.CitizenVoiceEngine;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.util.SoundUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@link SoundUtils} random citizen vocalizations and direct event triggers
 * to route them through the context-reactive {@link CitizenVoiceEngine}.
 */
@Mixin(value = SoundUtils.class, remap = false)
public abstract class SoundUtilsMixin {

    @Inject(method = "playRandomSound", at = @At("HEAD"), cancellable = true)
    private static void advancedcolonies$onPlayRandomSound(
            @NotNull final Level worldIn,
            @NotNull final BlockPos pos,
            @NotNull final ICitizenData citizen,
            final CallbackInfo ci) {
        if (CitizenVoiceEngine.handlePlayRandomSound(worldIn, pos, citizen)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "playSoundAtCitizenWith(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lcom/minecolonies/api/sounds/EventType;Lcom/minecolonies/api/colony/ICivilianData;DD)V",
            at = @At("HEAD"))
    private static void advancedcolonies$onPlaySoundAtCitizenWith(
            final Level worldIn,
            @NotNull final BlockPos pos,
            final EventType event,
            @Nullable final ICivilianData citizen,
            final double volume,
            final double pitch,
            final CallbackInfo ci) {
        CitizenVoiceEngine.onDirectSoundAtCitizen(worldIn, pos, event, citizen);
    }
}
