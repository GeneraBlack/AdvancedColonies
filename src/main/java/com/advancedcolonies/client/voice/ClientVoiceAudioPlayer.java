package com.advancedcolonies.client.voice;

import com.advancedcolonies.AdvancedColonies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only 3D audio dispatcher that plays the exact citizen voice clip matching
 * the active 3D speech bubble, bound directly to the moving citizen entity.
 */
public final class ClientVoiceAudioPlayer {

    /** Tracks the currently playing voice clip per citizen entity ID so a citizen never talks over themselves. */
    private static final Map<Integer, SoundInstance> ACTIVE_VOICE_SOUNDS = new ConcurrentHashMap<>();

    private ClientVoiceAudioPlayer() {
    }

    /**
     * Plays the specified {@code advancedcolonies} voice clip bound to the citizen entity in 3D space.
     *
     * @param entityId      the speaking citizen's runtime entity ID
     * @param soundEventKey the sound event key in {@code advancedcolonies/sounds.json} (e.g. {@code voice.male1.danger_01})
     * @param pitch         playback pitch multiplier
     */
    public static void playCitizenVoice(
            final int entityId,
            @NotNull final String soundEventKey,
            final float pitch) {

        if (soundEventKey.isEmpty()) {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        final Entity entity = level.getEntity(entityId);
        if (entity == null || !entity.isAlive()) {
            return;
        }

        final SoundManager soundManager = mc.getSoundManager();
        final SoundInstance previous = ACTIVE_VOICE_SOUNDS.remove(entityId);
        if (previous != null && soundManager.isActive(previous)) {
            soundManager.stop(previous);
        }

        final ResourceLocation soundLocation = ResourceLocation.fromNamespaceAndPath(
                AdvancedColonies.MOD_ID, soundEventKey);
        final SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundLocation);
        final EntityBoundSoundInstance instance = new EntityBoundSoundInstance(
                soundEvent,
                SoundSource.NEUTRAL,
                0.95F,
                Math.max(0.5F, Math.min(2.0F, pitch)),
                entity,
                level.random.nextLong());

        ACTIVE_VOICE_SOUNDS.put(entityId, instance);
        soundManager.play(instance);
    }

    /**
     * Clears tracked voice instances (e.g. on disconnect).
     */
    public static void clear() {
        ACTIVE_VOICE_SOUNDS.clear();
    }
}
