package com.advancedcolonies.network;

import com.advancedcolonies.AdvancedColonies;
import com.advancedcolonies.common.debug.CitizenDebugSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client packet payload carrying real-time {@link CitizenDebugSnapshot}s
 * and layer visibility flags for the in-game visual debugger.
 *
 * @param clearAll        {@code true} if the client should clear all active debug overlays
 * @param showActions     {@code true} to render AI action states above citizens
 * @param showStats       {@code true} to render health/saturation/happiness stats above citizens
 * @param showPathfinding {@code true} to render 3D pathfinding lines, nodes, and target blocks
 * @param snapshots       list of active citizen debug snapshots
 */
public record CitizenDebugSyncPayload(
        boolean clearAll,
        boolean showActions,
        boolean showStats,
        boolean showPathfinding,
        @NotNull List<CitizenDebugSnapshot> snapshots) implements CustomPacketPayload {

    public static final Type<CitizenDebugSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AdvancedColonies.MOD_ID, "citizen_debug_sync"));

    public static final StreamCodec<FriendlyByteBuf, CitizenDebugSyncPayload> STREAM_CODEC =
            StreamCodec.of(CitizenDebugSyncPayload::write, CitizenDebugSyncPayload::read);

    private static void write(@NotNull final FriendlyByteBuf buf, @NotNull final CitizenDebugSyncPayload payload) {
        buf.writeBoolean(payload.clearAll);
        buf.writeBoolean(payload.showActions);
        buf.writeBoolean(payload.showStats);
        buf.writeBoolean(payload.showPathfinding);
        buf.writeVarInt(payload.snapshots.size());
        for (final CitizenDebugSnapshot snapshot : payload.snapshots) {
            snapshot.write(buf);
        }
    }

    @NotNull
    private static CitizenDebugSyncPayload read(@NotNull final FriendlyByteBuf buf) {
        final boolean clearAll = buf.readBoolean();
        final boolean showActions = buf.readBoolean();
        final boolean showStats = buf.readBoolean();
        final boolean showPathfinding = buf.readBoolean();
        final int count = buf.readVarInt();
        final List<CitizenDebugSnapshot> snapshots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            snapshots.add(CitizenDebugSnapshot.read(buf));
        }
        return new CitizenDebugSyncPayload(clearAll, showActions, showStats, showPathfinding, snapshots);
    }

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
