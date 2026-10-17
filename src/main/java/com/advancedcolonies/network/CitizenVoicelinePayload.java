package com.advancedcolonies.network;

import com.advancedcolonies.AdvancedColonies;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Server-to-client network payload delivering a context-reactive voiceline
 * to be rendered as a floating 3D speech bubble above a speaking citizen.
 *
 * @param entityId       the runtime entity ID of the speaking citizen
 * @param citizenName    the citizen's display name
 * @param jobName        the citizen's job title
 * @param text           the formatted situational voiceline
 * @param accentColorRgb the 24-bit RGB accent color for the context
 * @param durationTicks  how many ticks the speech bubble remains visible
 */
public record CitizenVoicelinePayload(
        int entityId,
        @NotNull String citizenName,
        @NotNull String jobName,
        @NotNull String text,
        int accentColorRgb,
        int durationTicks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CitizenVoicelinePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AdvancedColonies.MOD_ID, "citizen_voiceline"));

    public static final StreamCodec<FriendlyByteBuf, CitizenVoicelinePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                @NotNull
                public CitizenVoicelinePayload decode(@NotNull final FriendlyByteBuf buf) {
                    final int entityId = buf.readVarInt();
                    final String citizenName = buf.readUtf(128);
                    final String jobName = buf.readUtf(64);
                    final String text = buf.readUtf(512);
                    final int accentColorRgb = buf.readInt();
                    final int durationTicks = buf.readVarInt();
                    return new CitizenVoicelinePayload(entityId, citizenName, jobName, text, accentColorRgb, durationTicks);
                }

                @Override
                public void encode(@NotNull final FriendlyByteBuf buf, @NotNull final CitizenVoicelinePayload payload) {
                    buf.writeVarInt(payload.entityId);
                    buf.writeUtf(payload.citizenName, 128);
                    buf.writeUtf(payload.jobName, 64);
                    buf.writeUtf(payload.text, 512);
                    buf.writeInt(payload.accentColorRgb);
                    buf.writeVarInt(payload.durationTicks);
                }
            };

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
