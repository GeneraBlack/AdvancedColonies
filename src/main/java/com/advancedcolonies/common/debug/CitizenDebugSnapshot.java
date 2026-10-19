package com.advancedcolonies.common.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of a citizen's live AI actions, vitals/stats, 3D movement & stall telemetry,
 * inventory/equipment, open requests, Builder construction progress, and 3D pathfinding nodes
 * captured on the logical server and synchronized to subscribed clients for visual debugging.
 */
public record CitizenDebugSnapshot(
        int entityId,
        int citizenId,
        int colonyId,
        @NotNull String citizenName,
        @NotNull String jobName,
        @NotNull String citizenAiState,
        @NotNull String workerAiState,
        float health,
        float maxHealth,
        double saturation,
        double happiness,
        int stuckLevel,
        int globalTimeoutTicks,
        int realStalledTicks,
        float moveSpeedBps,
        float distanceToTarget,
        @NotNull String rescueAction,
        @NotNull String workBuildingInfo,
        @Nullable BlockPos workBuildingPos,
        @NotNull String heldItemName,
        int inventoryUsedSlots,
        int inventoryTotalSlots,
        @NotNull String activeRequestSummary,
        @NotNull String builderStageInfo,
        @Nullable BlockPos workTargetPos,
        @Nullable BlockPos workStandPos,
        @NotNull String pathStatus,
        @Nullable BlockPos targetPos,
        int currentNodeIndex,
        @NotNull List<PathNodeEntry> pathNodes) {

    /** Maximum number of path nodes serialized per citizen snapshot to keep packets compact. */
    public static final int MAX_SERIALIZED_NODES = 64;

    /**
     * Returns {@code true} only when the citizen is genuinely physically stalled (3+ seconds of zero spatial progress
     * while trying to navigate) or in an active MineColonies stuck recovery level.
     */
    public boolean isGenuinelyStuck() {
        return this.realStalledTicks >= 60 || this.stuckLevel > 0;
    }

    /**
     * Represents a single 3D pathfinding node for client-side path line and box rendering.
     *
     * @param x        block X coordinate
     * @param y        block Y coordinate
     * @param z        block Z coordinate
     * @param onLadder {@code true} if the node is a ladder/vine climb node
     * @param onRails  {@code true} if the node is a minecart rail node
     * @param reached  {@code true} if the citizen has already passed this node
     */
    public record PathNodeEntry(
            int x,
            int y,
            int z,
            boolean onLadder,
            boolean onRails,
            boolean reached) {

        public void write(@NotNull final FriendlyByteBuf buf) {
            buf.writeVarInt(this.x);
            buf.writeVarInt(this.y);
            buf.writeVarInt(this.z);
            int flags = 0;
            if (this.onLadder) {
                flags |= 1;
            }
            if (this.onRails) {
                flags |= 2;
            }
            if (this.reached) {
                flags |= 4;
            }
            buf.writeByte(flags);
        }

        @NotNull
        public static PathNodeEntry read(@NotNull final FriendlyByteBuf buf) {
            final int x = buf.readVarInt();
            final int y = buf.readVarInt();
            final int z = buf.readVarInt();
            final byte flags = buf.readByte();
            return new PathNodeEntry(
                    x,
                    y,
                    z,
                    (flags & 1) != 0,
                    (flags & 2) != 0,
                    (flags & 4) != 0);
        }
    }

    /**
     * Writes this snapshot into a network byte buffer.
     *
     * @param buf the target byte buffer
     */
    public void write(@NotNull final FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeVarInt(this.citizenId);
        buf.writeVarInt(this.colonyId);
        buf.writeUtf(this.citizenName, 64);
        buf.writeUtf(this.jobName, 64);
        buf.writeUtf(this.citizenAiState, 64);
        buf.writeUtf(this.workerAiState, 64);
        buf.writeFloat(this.health);
        buf.writeFloat(this.maxHealth);
        buf.writeDouble(this.saturation);
        buf.writeDouble(this.happiness);
        buf.writeVarInt(this.stuckLevel);
        buf.writeVarInt(this.globalTimeoutTicks);
        buf.writeVarInt(this.realStalledTicks);
        buf.writeFloat(this.moveSpeedBps);
        buf.writeFloat(this.distanceToTarget);
        buf.writeUtf(this.rescueAction, 32);
        buf.writeUtf(this.workBuildingInfo, 64);
        buf.writeBoolean(this.workBuildingPos != null);
        if (this.workBuildingPos != null) {
            buf.writeBlockPos(this.workBuildingPos);
        }
        buf.writeUtf(this.heldItemName, 64);
        buf.writeVarInt(this.inventoryUsedSlots);
        buf.writeVarInt(this.inventoryTotalSlots);
        buf.writeUtf(this.activeRequestSummary, 96);
        buf.writeUtf(this.builderStageInfo, 96);
        buf.writeBoolean(this.workTargetPos != null);
        if (this.workTargetPos != null) {
            buf.writeBlockPos(this.workTargetPos);
        }
        buf.writeBoolean(this.workStandPos != null);
        if (this.workStandPos != null) {
            buf.writeBlockPos(this.workStandPos);
        }
        buf.writeUtf(this.pathStatus, 48);
        buf.writeBoolean(this.targetPos != null);
        if (this.targetPos != null) {
            buf.writeBlockPos(this.targetPos);
        }
        buf.writeVarInt(this.currentNodeIndex);
        final int nodeCount = Math.min(this.pathNodes.size(), MAX_SERIALIZED_NODES);
        buf.writeVarInt(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            this.pathNodes.get(i).write(buf);
        }
    }

    /**
     * Reads a {@link CitizenDebugSnapshot} from a network byte buffer.
     *
     * @param buf the source byte buffer
     * @return the decoded snapshot
     */
    @NotNull
    public static CitizenDebugSnapshot read(@NotNull final FriendlyByteBuf buf) {
        final int entityId = buf.readVarInt();
        final int citizenId = buf.readVarInt();
        final int colonyId = buf.readVarInt();
        final String citizenName = buf.readUtf(64);
        final String jobName = buf.readUtf(64);
        final String citizenAiState = buf.readUtf(64);
        final String workerAiState = buf.readUtf(64);
        final float health = buf.readFloat();
        final float maxHealth = buf.readFloat();
        final double saturation = buf.readDouble();
        final double happiness = buf.readDouble();
        final int stuckLevel = buf.readVarInt();
        final int globalTimeoutTicks = buf.readVarInt();
        final int realStalledTicks = buf.readVarInt();
        final float moveSpeedBps = buf.readFloat();
        final float distanceToTarget = buf.readFloat();
        final String rescueAction = buf.readUtf(32);
        final String workBuildingInfo = buf.readUtf(64);
        final BlockPos workBuildingPos = buf.readBoolean() ? buf.readBlockPos() : null;
        final String heldItemName = buf.readUtf(64);
        final int inventoryUsedSlots = buf.readVarInt();
        final int inventoryTotalSlots = buf.readVarInt();
        final String activeRequestSummary = buf.readUtf(96);
        final String builderStageInfo = buf.readUtf(96);
        final BlockPos workTargetPos = buf.readBoolean() ? buf.readBlockPos() : null;
        final BlockPos workStandPos = buf.readBoolean() ? buf.readBlockPos() : null;
        final String pathStatus = buf.readUtf(48);
        final BlockPos targetPos = buf.readBoolean() ? buf.readBlockPos() : null;
        final int currentNodeIndex = buf.readVarInt();
        final int nodeCount = buf.readVarInt();
        final List<PathNodeEntry> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(PathNodeEntry.read(buf));
        }
        return new CitizenDebugSnapshot(
                entityId,
                citizenId,
                colonyId,
                citizenName,
                jobName,
                citizenAiState,
                workerAiState,
                health,
                maxHealth,
                saturation,
                happiness,
                stuckLevel,
                globalTimeoutTicks,
                realStalledTicks,
                moveSpeedBps,
                distanceToTarget,
                rescueAction,
                workBuildingInfo,
                workBuildingPos,
                heldItemName,
                inventoryUsedSlots,
                inventoryTotalSlots,
                activeRequestSummary,
                builderStageInfo,
                workTargetPos,
                workStandPos,
                pathStatus,
                targetPos,
                currentNodeIndex,
                nodes);
    }
}
