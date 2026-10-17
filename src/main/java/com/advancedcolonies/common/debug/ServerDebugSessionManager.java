package com.advancedcolonies.common.debug;

import com.advancedcolonies.AdvancedColonies;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.network.CitizenDebugSyncPayload;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.ai.ITickingStateAI;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.PathPointExtended;
import com.minecolonies.core.entity.pathfinding.navigation.AbstractAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player visual debug sessions on the logical server, collects live
 * {@link CitizenDebugSnapshot}s, and dispatches {@link CitizenDebugSyncPayload} packets.
 */
@EventBusSubscriber(modid = AdvancedColonies.MOD_ID)
public final class ServerDebugSessionManager {

    /** Per-player active debug configuration. */
    public static final class PlayerDebugSession {
        private final Set<Integer> watchedColonies = new HashSet<>();
        private final Map<Integer, Set<Integer>> watchedCitizensByColony = new HashMap<>();
        private boolean showActions = true;
        private boolean showStats = true;
        private boolean showPathfinding = true;

        public Set<Integer> getWatchedColonies() {
            return Collections.unmodifiableSet(this.watchedColonies);
        }

        public Map<Integer, Set<Integer>> getWatchedCitizensByColony() {
            return Collections.unmodifiableMap(this.watchedCitizensByColony);
        }

        public boolean isShowActions() {
            return this.showActions;
        }

        public void setShowActions(final boolean showActions) {
            this.showActions = showActions;
        }

        public boolean isShowStats() {
            return this.showStats;
        }

        public void setShowStats(final boolean showStats) {
            this.showStats = showStats;
        }

        public boolean isShowPathfinding() {
            return this.showPathfinding;
        }

        public void setShowPathfinding(final boolean showPathfinding) {
            this.showPathfinding = showPathfinding;
        }

        public boolean isEmpty() {
            return this.watchedColonies.isEmpty() && this.watchedCitizensByColony.isEmpty();
        }

        public void clear() {
            this.watchedColonies.clear();
            this.watchedCitizensByColony.clear();
        }
    }

    /** Live stuck metrics reported by {@code PathingStuckHandlerMixin}. */
    public record StuckMetrics(int stuckLevel, int globalTimeoutTicks, long gameTime) {
    }

    private static final Map<UUID, PlayerDebugSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, StuckMetrics> STUCK_METRICS = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    private ServerDebugSessionManager() {
    }

    /**
     * Records live stuck metrics for a citizen entity from {@code PathingStuckHandler}.
     *
     * @param entityId           the runtime entity ID
     * @param stuckLevel         the current stuck level
     * @param globalTimeoutTicks the current global timeout ticks
     * @param gameTime           the server level game time
     */
    public static void recordStuckMetrics(
            final int entityId,
            final int stuckLevel,
            final int globalTimeoutTicks,
            final long gameTime) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        STUCK_METRICS.put(entityId, new StuckMetrics(stuckLevel, globalTimeoutTicks, gameTime));
    }

    /**
     * Returns or creates the debug session for a player.
     */
    @NotNull
    public static PlayerDebugSession getOrCreateSession(@NotNull final ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), k -> new PlayerDebugSession());
    }

    /**
     * Returns the existing debug session for a player, or {@code null} if inactive.
     */
    @Nullable
    public static PlayerDebugSession getSession(@NotNull final ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    /**
     * Sets or toggles colony-wide visual debugging for a player.
     *
     * @param player   the player
     * @param colonyId the target colony ID
     * @param enable   {@code true} to enable, {@code false} to disable, or {@code null} to toggle
     * @return {@code true} if colony debugging is now enabled, {@code false} if disabled
     */
    public static boolean setColonyDebug(
            @NotNull final ServerPlayer player,
            final int colonyId,
            @Nullable final Boolean enable) {
        final PlayerDebugSession session = getOrCreateSession(player);
        final boolean currentlyEnabled = session.watchedColonies.contains(colonyId);
        final boolean targetState = enable != null ? enable : !currentlyEnabled;
        if (targetState) {
            session.watchedColonies.add(colonyId);
        } else {
            session.watchedColonies.remove(colonyId);
        }
        cleanupAndSync(player, session);
        return targetState;
    }

    /**
     * Sets or toggles per-citizen visual debugging for a player.
     *
     * @param player    the player
     * @param colonyId  the citizen's colony ID
     * @param citizenId the citizen's civilian ID
     * @param enable    {@code true} to enable, {@code false} to disable, or {@code null} to toggle
     * @return {@code true} if debugging for this citizen is now enabled, {@code false} if disabled
     */
    public static boolean setCitizenDebug(
            @NotNull final ServerPlayer player,
            final int colonyId,
            final int citizenId,
            @Nullable final Boolean enable) {
        final PlayerDebugSession session = getOrCreateSession(player);
        final Set<Integer> citizens = session.watchedCitizensByColony.computeIfAbsent(colonyId, k -> new HashSet<>());
        final boolean currentlyEnabled = citizens.contains(citizenId);
        final boolean targetState = enable != null ? enable : !currentlyEnabled;
        if (targetState) {
            citizens.add(citizenId);
        } else {
            citizens.remove(citizenId);
            if (citizens.isEmpty()) {
                session.watchedCitizensByColony.remove(colonyId);
            }
        }
        cleanupAndSync(player, session);
        return targetState;
    }

    /**
     * Clears all active visual debugging targets for a player and sends an immediate clear packet.
     */
    public static void clearAll(@NotNull final ServerPlayer player) {
        final PlayerDebugSession session = SESSIONS.remove(player.getUUID());
        final boolean showActions = session == null || session.showActions;
        final boolean showStats = session == null || session.showStats;
        final boolean showPathfinding = session == null || session.showPathfinding;
        PacketDistributor.sendToPlayer(
                player,
                new CitizenDebugSyncPayload(true, showActions, showStats, showPathfinding, List.of()));
    }

    /**
     * Immediately synchronizes the player's current debug session to their client.
     */
    public static void cleanupAndSync(@NotNull final ServerPlayer player, @NotNull final PlayerDebugSession session) {
        if (session.isEmpty()) {
            SESSIONS.remove(player.getUUID());
            PacketDistributor.sendToPlayer(
                    player,
                    new CitizenDebugSyncPayload(
                            true,
                            session.showActions,
                            session.showStats,
                            session.showPathfinding,
                            List.of()));
        } else {
            syncPlayerSession(player, session);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(@NotNull final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            SESSIONS.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(@NotNull final ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableVisualDebugger.get()) {
            return;
        }

        final int interval = Math.max(1, AdvancedColoniesConfig.CITIZEN_COMMON.visualDebugSyncIntervalTicks.get());
        if (++tickCounter % interval != 0) {
            return;
        }

        for (final ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            final PlayerDebugSession session = SESSIONS.get(player.getUUID());
            if (session != null && !session.isEmpty()) {
                syncPlayerSession(player, session);
            }
        }
    }

    private static void syncPlayerSession(
            @NotNull final ServerPlayer player,
            @NotNull final PlayerDebugSession session) {
        final List<CitizenDebugSnapshot> snapshots = new ArrayList<>();
        final Set<Integer> seenCitizenKeys = new HashSet<>();
        final int maxRange = AdvancedColoniesConfig.CITIZEN_COMMON.visualDebugMaxRange.get();
        final double maxRangeSqr = (double) maxRange * (double) maxRange;

        // 1. Collect all citizens from watched colonies within maxRange
        for (final int colonyId : session.watchedColonies) {
            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, player.level().dimension());
            if (colony == null) {
                continue;
            }
            for (final ICitizenData data : colony.getCitizenManager().getCitizens()) {
                final Optional<AbstractEntityCitizen> entityOpt = data.getEntity();
                if (entityOpt.isPresent() && entityOpt.get() instanceof EntityCitizen citizen) {
                    if (citizen.distanceToSqr(player) <= maxRangeSqr) {
                        seenCitizenKeys.add( citizen.getId());
                        snapshots.add(buildSnapshot(colonyId, data, citizen));
                    }
                }
            }
        }

        // 2. Collect explicitly watched individual citizens
        for (final Map.Entry<Integer, Set<Integer>> entry : session.watchedCitizensByColony.entrySet()) {
            final int colonyId = entry.getKey();
            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, player.level().dimension());
            if (colony == null) {
                continue;
            }
            for (final int citizenId : entry.getValue()) {
                final ICitizenData data = colony.getCitizenManager().getCivilian(citizenId);
                if (data == null) {
                    continue;
                }
                final Optional<AbstractEntityCitizen> entityOpt = data.getEntity();
                if (entityOpt.isPresent() && entityOpt.get() instanceof EntityCitizen citizen) {
                    if (seenCitizenKeys.add(citizen.getId())) {
                        snapshots.add(buildSnapshot(colonyId, data, citizen));
                    }
                }
            }
        }

        PacketDistributor.sendToPlayer(
                player,
                new CitizenDebugSyncPayload(
                        false,
                        session.showActions,
                        session.showStats,
                        session.showPathfinding,
                        snapshots));
    }

    @NotNull
    private static CitizenDebugSnapshot buildSnapshot(
            final int colonyId,
            @NotNull final ICitizenData data,
            @NotNull final EntityCitizen citizen) {
        final String name = data.getName();
        String jobName = "Unemployed";
        String workerAiState = "None";

        final IJob<?> job = data.getJob();
        if (job != null) {
            jobName = Component.translatable(job.getJobRegistryEntry().getTranslationKey()).getString();
            final ITickingStateAI workAi = job.getWorkerAI();
            if (workAi != null && workAi.getStateAI() != null && workAi.getStateAI().getState() != null) {
                workerAiState = workAi.getStateAI().getState().toString();
            }
        }

        final IState topState = citizen.getCitizenAI() != null ? citizen.getCitizenAI().getState() : null;
        final String citizenAiState = topState != null ? topState.toString() : "IDLE";

        final float health = citizen.getHealth();
        final float maxHealth = citizen.getMaxHealth();
        final double saturation = data.getSaturation();
        final double happiness = data.getCitizenHappinessHandler() != null
                ? data.getCitizenHappinessHandler().getHappiness(data.getColony(), data)
                : 0.0D;

        final StuckMetrics stuck = STUCK_METRICS.get(citizen.getId());
        final long now = citizen.level().getGameTime();
        final int stuckLevel = (stuck != null && (now - stuck.gameTime()) < 100L) ? stuck.stuckLevel() : 0;
        final int globalTimeout = (stuck != null && (now - stuck.gameTime()) < 100L) ? stuck.globalTimeoutTicks() : 0;

        String pathStatus = "IDLE";
        BlockPos targetPos = null;
        int currentNodeIndex = 0;
        final List<CitizenDebugSnapshot.PathNodeEntry> nodes = new ArrayList<>();

        final PathNavigation nav = citizen.getNavigation();
        if (nav != null) {
            targetPos = nav.getTargetPos();
            if (nav instanceof AbstractAdvancedPathNavigate advNav) {
                final PathResult<?> result = advNav.getPathResult();
                if (result != null && result.getStatus() != null) {
                    pathStatus = result.getStatus().name();
                } else if (!nav.isDone()) {
                    pathStatus = "MOVING";
                }
            } else if (!nav.isDone()) {
                pathStatus = "MOVING";
            }

            final Path path = nav.getPath();
            if (path != null && path.getNodeCount() > 0) {
                currentNodeIndex = path.getNextNodeIndex();
                final int totalNodes = path.getNodeCount();
                final int startIdx = Math.max(0, currentNodeIndex - 8);
                final int endIdx = Math.min(totalNodes, startIdx + CitizenDebugSnapshot.MAX_SERIALIZED_NODES);
                for (int i = startIdx; i < endIdx; i++) {
                    final Node node = path.getNode(i);
                    boolean onLadder = false;
                    boolean onRails = false;
                    if (node instanceof PathPointExtended ext) {
                        onLadder = ext.isOnLadder();
                        onRails = ext.isOnRails();
                    }
                    final boolean reached = i < currentNodeIndex;
                    nodes.add(new CitizenDebugSnapshot.PathNodeEntry(
                            node.x,
                            node.y,
                            node.z,
                            onLadder,
                            onRails,
                            reached));
                }
                currentNodeIndex = Math.max(0, currentNodeIndex - startIdx);
            }
        }

        return new CitizenDebugSnapshot(
                citizen.getId(),
                data.getId(),
                colonyId,
                name,
                jobName,
                citizenAiState,
                workerAiState,
                health,
                maxHealth,
                saturation,
                happiness,
                stuckLevel,
                globalTimeout,
                pathStatus,
                targetPos,
                currentNodeIndex,
                nodes);
    }
}
