package com.advancedcolonies.client.voice;

import com.advancedcolonies.network.CitizenVoicelinePayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe client-side store for active floating 3D citizen speech bubbles.
 */
public final class ClientSpeechBubbleCache {

    /**
     * Represents an active speech bubble above a citizen entity.
     *
     * @param entityId       the citizen's runtime entity ID
     * @param citizenName    the citizen's display name
     * @param jobName        the citizen's job display name
     * @param text           the situational voiceline text
     * @param accentColorRgb the 24-bit RGB border/header accent color
     * @param startTimeMs    system timestamp when received
     * @param durationMs     total display duration in milliseconds
     */
    public record SpeechBubbleEntry(
            int entityId,
            @NotNull String citizenName,
            @NotNull String jobName,
            @NotNull String text,
            int accentColorRgb,
            long startTimeMs,
            long durationMs) {

        /**
         * Computes the opacity multiplier (0.0 .. 1.0) with smooth fade-in and fade-out.
         */
        public float computeAlpha(final long nowMs) {
            final long elapsed = nowMs - startTimeMs;
            if (elapsed < 0L || elapsed >= durationMs) {
                return 0.0F;
            }
            if (elapsed < 250L) {
                return Math.min(1.0F, elapsed / 250.0F);
            }
            final long remaining = durationMs - elapsed;
            if (remaining < 600L) {
                return Math.max(0.0F, remaining / 600.0F);
            }
            return 1.0F;
        }
    }

    private static final Map<Integer, SpeechBubbleEntry> ACTIVE_BUBBLES = new ConcurrentHashMap<>();

    private ClientSpeechBubbleCache() {
    }

    /**
     * Registers an incoming {@link CitizenVoicelinePayload} from the server.
     *
     * @param payload the voiceline payload
     */
    public static void applyPayload(@NotNull final CitizenVoicelinePayload payload) {
        final long durationMs = Math.max(1500L, payload.durationTicks() * 50L);
        ACTIVE_BUBBLES.put(
                payload.entityId(),
                new SpeechBubbleEntry(
                        payload.entityId(),
                        payload.citizenName(),
                        payload.jobName(),
                        payload.text(),
                        payload.accentColorRgb(),
                        System.currentTimeMillis(),
                        durationMs));
    }

    /**
     * Retrieves the active, non-expired speech bubble for a citizen entity, or {@code null}.
     *
     * @param entityId the runtime entity ID
     * @return the active speech bubble entry, or {@code null}
     */
    @Nullable
    public static SpeechBubbleEntry getActiveBubble(final int entityId) {
        final SpeechBubbleEntry entry = ACTIVE_BUBBLES.get(entityId);
        if (entry == null) {
            return null;
        }
        final long nowMs = System.currentTimeMillis();
        if (nowMs - entry.startTimeMs() >= entry.durationMs()) {
            ACTIVE_BUBBLES.remove(entityId);
            return null;
        }
        return entry;
    }

    /**
     * Clears all active speech bubbles (e.g. on client disconnect).
     */
    public static void clear() {
        ACTIVE_BUBBLES.clear();
    }
}
