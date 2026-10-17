package com.advancedcolonies.network;

import com.advancedcolonies.client.debug.ClientDebugCache;
import com.advancedcolonies.client.voice.ClientSpeechBubbleCache;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;

/**
 * Registers custom network payloads for {@code AdvancedColonies}.
 */
public final class AdvancedColoniesNetwork {

    private static final String PROTOCOL_VERSION = "1";

    private AdvancedColoniesNetwork() {
    }

    /**
     * Registers all mod packets on the mod event bus.
     *
     * @param event the payload registration event
     */
    public static void onRegisterPayloads(@NotNull final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToClient(
                CitizenDebugSyncPayload.TYPE,
                CitizenDebugSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientDebugCache.applyPayload(payload)));
        registrar.playToClient(
                CitizenVoicelinePayload.TYPE,
                CitizenVoicelinePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientSpeechBubbleCache.applyPayload(payload)));
    }
}
