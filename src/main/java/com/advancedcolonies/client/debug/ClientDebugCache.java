package com.advancedcolonies.client.debug;

import com.advancedcolonies.common.debug.CitizenDebugSnapshot;
import com.advancedcolonies.network.CitizenDebugSyncPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe client-side store for the latest synchronized {@link CitizenDebugSnapshot}s
 * and active visual layer flags.
 */
public final class ClientDebugCache {

    /** Expire snapshots automatically if no update has been received for 4 seconds. */
    private static final long SNAPSHOT_TIMEOUT_MS = 4000L;

    private static final Map<Integer, CitizenDebugSnapshot> BY_ENTITY_ID = new ConcurrentHashMap<>();
    private static volatile long lastUpdateMillis = 0L;
    private static volatile boolean showActions = true;
    private static volatile boolean showStats = true;
    private static volatile boolean showPathfinding = true;

    private ClientDebugCache() {
    }

    /**
     * Applies an incoming {@link CitizenDebugSyncPayload} from the server.
     *
     * @param payload the sync payload
     */
    public static void applyPayload(@NotNull final CitizenDebugSyncPayload payload) {
        showActions = payload.showActions();
        showStats = payload.showStats();
        showPathfinding = payload.showPathfinding();

        BY_ENTITY_ID.clear();
        if (payload.clearAll() || payload.snapshots().isEmpty()) {
            lastUpdateMillis = 0L;
            return;
        }

        lastUpdateMillis = System.currentTimeMillis();
        for (final CitizenDebugSnapshot snapshot : payload.snapshots()) {
            BY_ENTITY_ID.put(snapshot.entityId(), snapshot);
        }
    }

    /**
     * Clears all cached debug snapshots immediately (e.g. on disconnect or world unload).
     */
    public static void clear() {
        BY_ENTITY_ID.clear();
        lastUpdateMillis = 0L;
    }

    /**
     * Returns {@code true} if the visual debugger currently has fresh, non-expired snapshots.
     */
    public static boolean isActive() {
        if (BY_ENTITY_ID.isEmpty()) {
            return false;
        }
        if (System.currentTimeMillis() - lastUpdateMillis > SNAPSHOT_TIMEOUT_MS) {
            BY_ENTITY_ID.clear();
            return false;
        }
        return true;
    }

    /**
     * Retrieves the debug snapshot for a specific runtime entity ID, or {@code null} if not tracked.
     *
     * @param entityId the runtime entity ID
     * @return the snapshot, or {@code null}
     */
    @Nullable
    public static CitizenDebugSnapshot getByEntityId(final int entityId) {
        if (!isActive()) {
            return null;
        }
        return BY_ENTITY_ID.get(entityId);
    }

    /**
     * Returns all currently active citizen debug snapshots.
     */
    @NotNull
    public static Collection<CitizenDebugSnapshot> getAllSnapshots() {
        if (!isActive()) {
            return java.util.List.of();
        }
        return BY_ENTITY_ID.values();
    }

    public static boolean isShowActions() {
        return showActions;
    }

    public static boolean isShowStats() {
        return showStats;
    }

    public static boolean isShowPathfinding() {
        return showPathfinding;
    }
}
