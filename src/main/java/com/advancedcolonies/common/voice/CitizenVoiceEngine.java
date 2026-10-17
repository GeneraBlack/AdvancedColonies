package com.advancedcolonies.common.voice;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.CitizenCommonConfig;
import com.advancedcolonies.network.CitizenVoicelinePayload;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.citizen.AbstractCivilianEntity;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context-reactive citizen voiceline and smart audio routing engine:
 * <ul>
 *   <li>Evaluates a citizen's live surroundings (actual sky exposure during rain/storms, active raids,
 *       hostile mobs, health/retreat, sickness, mourning, hunger, restaurant/home proximity, nearby
 *       patrolling Guards, nearby players, and current AI work state).</li>
 *   <li>Routes MineColonies / TownTalk audio playback to the matching {@link EventType} so voice audio
 *       never contradicts what the citizen is experiencing.</li>
 *   <li>Generates dynamic English voicelines with resolved placeholders and broadcasts floating 3D
 *       speech bubbles to nearby players while strictly enforcing server cooldown and range limits.</li>
 * </ul>
 */
public final class CitizenVoiceEngine {

    /** Per-citizen last voiceline timestamp (game time ticks). */
    private static final Map<Long, Long> CITIZEN_LAST_SPOKE_TICK = new ConcurrentHashMap<>();

    /** Per-chunk-sector (16x16 area) last ambient voiceline timestamp to prevent crowd chatter spam. */
    private static final Map<Long, Long> AREA_LAST_SPOKE_TICK = new ConcurrentHashMap<>();

    /** Per-citizen last chosen template index to avoid repeating the exact same line twice in a row. */
    private static final Map<Long, Integer> CITIZEN_LAST_TEMPLATE_IDX = new ConcurrentHashMap<>();

    /** Re-entrancy guard when our engine invokes {@code SoundUtils.playSoundAtCitizenWith}. */
    private static final ThreadLocal<Boolean> INTERNAL_SOUND_DISPATCH = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private CitizenVoiceEngine() {
    }

    /**
     * Returns {@code true} if the current thread is inside an internal sound dispatch initiated by this engine.
     */
    public static boolean isInternalSoundDispatch() {
        return Boolean.TRUE.equals(INTERNAL_SOUND_DISPATCH.get());
    }

    /**
     * Handles {@code SoundUtils.playRandomSound(Level, BlockPos, ICitizenData)} when context-reactive
     * voicelines or smart sound routing is enabled.
     *
     * @param world   the level
     * @param pos     the citizen block position
     * @param citizen the citizen data
     * @return {@code true} if handled by the context-reactive engine (cancelling vanilla random roll)
     */
    public static boolean handlePlayRandomSound(
            @Nullable final Level world,
            @Nullable final BlockPos pos,
            @Nullable final ICitizenData citizen) {

        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        if (!cfg.enableContextReactiveVoicelines.get() || world == null || pos == null || citizen == null) {
            return false;
        }

        if (!(world instanceof ServerLevel serverLevel) || citizen.getColony() == null) {
            return false;
        }

        final int maxRange = Math.max(8, Math.min(64, cfg.voicelineMaxRangeBlocks.get()));
        final double maxRangeSqr = (double) maxRange * maxRange;

        Player nearestPlayer = null;
        double nearestPlayerDistSqr = Double.MAX_VALUE;
        for (final Player subscriber : citizen.getColony().getPackageManager().getCloseSubscribers()) {
            final double distSqr = subscriber.blockPosition().distSqr(pos);
            if (distSqr <= maxRangeSqr && distSqr < nearestPlayerDistSqr) {
                nearestPlayerDistSqr = distSqr;
                nearestPlayer = subscriber;
            }
        }

        if (nearestPlayer == null) {
            return true;
        }

        final long nowTick = serverLevel.getGameTime();
        if (!checkAndRecordCooldowns(citizen, pos, nowTick, false)) {
            return true;
        }

        final ResolvedVoiceSituation situation = evaluateSituation(serverLevel, pos, citizen, nearestPlayer, nearestPlayerDistSqr, null);
        dispatchAudioAndSpeechBubble(serverLevel, pos, citizen, situation);
        return true;
    }

    /**
     * Intercepts direct calls to {@code SoundUtils.playSoundAtCitizenWith} so explicit interactions
     * (greetings, farewells, sickness, danger) also produce matching floating 3D speech bubbles
     * and respect smart context validation.
     *
     * @param world     the level
     * @param pos       the citizen position
     * @param eventType the requested MineColonies sound event type
     * @param civilian  the civilian/citizen data
     */
    public static void onDirectSoundAtCitizen(
            @Nullable final Level world,
            @Nullable final BlockPos pos,
            @Nullable final EventType eventType,
            @Nullable final ICivilianData civilian) {

        if (isInternalSoundDispatch()
                || !(world instanceof ServerLevel serverLevel)
                || pos == null
                || eventType == null
                || !(civilian instanceof ICitizenData citizen)) {
            return;
        }

        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        if (!cfg.enableContextReactiveVoicelines.get() || !cfg.enableFloatingSpeechBubbles.get()) {
            return;
        }

        final long nowTick = serverLevel.getGameTime();
        if (!checkAndRecordCooldowns(citizen, pos, nowTick, true)) {
            return;
        }

        final int maxRange = Math.max(8, Math.min(64, cfg.voicelineMaxRangeBlocks.get()));
        final Player nearestPlayer = serverLevel.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, maxRange, false);
        if (nearestPlayer == null) {
            return;
        }

        final double distSqr = nearestPlayer.blockPosition().distSqr(pos);
        final ResolvedVoiceSituation situation = evaluateSituation(serverLevel, pos, citizen, nearestPlayer, distSqr, eventType);
        broadcastSpeechBubble(serverLevel, pos, citizen, situation);
    }

    /**
     * Enforces per-citizen and per-area server cooldown limits.
     */
    private static boolean checkAndRecordCooldowns(
            @NotNull final ICitizenData citizen,
            @NotNull final BlockPos pos,
            final long nowTick,
            final boolean isDirectEvent) {

        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        final int colonyId = citizen.getColony() != null ? citizen.getColony().getID() : 0;
        final long citizenKey = (((long) colonyId) << 32) | (citizen.getId() & 0xFFFFFFFFL);

        final int minCitizenSeconds = Math.max(5, Math.min(600, cfg.voicelineMinCitizenCooldownSeconds.get()));
        final long minCitizenTicks = isDirectEvent
                ? Math.max(60L, (minCitizenSeconds * 20L) / 3L)
                : minCitizenSeconds * 20L;

        final Long lastCitizenTick = CITIZEN_LAST_SPOKE_TICK.get(citizenKey);
        if (lastCitizenTick != null && (nowTick - lastCitizenTick) < minCitizenTicks) {
            return false;
        }

        final int minAreaTicks = Math.max(0, Math.min(1200, cfg.voicelineMinAreaCooldownTicks.get()));
        final long areaKey = (((long) (pos.getX() >> 4)) << 32) ^ ((pos.getZ() >> 4) & 0xFFFFFFFFL);
        if (!isDirectEvent && minAreaTicks > 0) {
            final Long lastAreaTick = AREA_LAST_SPOKE_TICK.get(areaKey);
            if (lastAreaTick != null && (nowTick - lastAreaTick) < minAreaTicks) {
                return false;
            }
        }

        CITIZEN_LAST_SPOKE_TICK.put(citizenKey, nowTick);
        AREA_LAST_SPOKE_TICK.put(areaKey, nowTick);
        return true;
    }

    /**
     * Evaluates the citizen's live environment, AI state, health, weather, and nearby entities
     * to determine the most coherent {@link CitizenVoiceContext}.
     */
    @NotNull
    private static ResolvedVoiceSituation evaluateSituation(
            @NotNull final ServerLevel world,
            @NotNull final BlockPos pos,
            @NotNull final ICitizenData citizen,
            @NotNull final Player nearestPlayer,
            final double nearestPlayerDistSqr,
            @Nullable final EventType explicitEventHint) {

        final IColony colony = citizen.getColony();
        final AbstractEntityCitizen entity = citizen.getEntity().orElse(null);
        final IJob<?> job = citizen.getJob();
        final boolean isGuard = job instanceof AbstractJobGuard<?>;

        final String citizenState = (entity instanceof EntityCitizen ec && ec.getCitizenAI() != null && ec.getCitizenAI().getState() != null)
                ? ec.getCitizenAI().getState().toString()
                : "";
        final String workerState = (job != null && job.getWorkerAI() instanceof AbstractEntityAIBasic<?, ?> ai && ai.getState() != null)
                ? ai.getState().toString()
                : "";

        // 1. Check active combat, guard retreat, raid, or nearby hostile monsters
        if (isGuard && workerState.contains("FLEE")) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.GUARD_RETREAT, nearestPlayer, null, null);
        }

        final boolean raidActive = colony != null && colony.getRaiderManager() != null && colony.getRaiderManager().isRaided();
        final boolean hostilesNearby = !world.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                new AABB(pos).inflate(16.0D),
                e -> e instanceof Enemy && e.isAlive()).isEmpty();

        if (raidActive || hostilesNearby || workerState.contains("ATTACK") || citizenState.contains("FLEE")) {
            final CitizenVoiceContext combatContext = isGuard
                    ? CitizenVoiceContext.GUARD_COMBAT
                    : CitizenVoiceContext.RAID_COMBAT;
            return new ResolvedVoiceSituation(combatContext, nearestPlayer, null, null);
        }

        // 2. Check Sickness / Hospital visit
        final boolean isSick = (citizen.getCitizenDiseaseHandler() != null && citizen.getCitizenDiseaseHandler().isSick())
                || citizenState.contains("SICK")
                || workerState.contains("SICK");
        if (isSick || explicitEventHint == EventType.SICKNESS) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.SICK_OR_INJURED, nearestPlayer, null, null);
        }

        // 3. Check Mourning
        if (citizenState.contains("MOURN") || citizen.getCitizenMournHandler().isMourning()) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.MOURNING, nearestPlayer, null, null);
        }

        // 4. Check Sleeping / Bedtime
        if (citizen.isAsleep() || citizenState.contains("SLEEP") || explicitEventHint == EventType.OFF_TO_BED) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.SLEEPING_OR_BEDTIME, nearestPlayer, null, null);
        }

        // 5. Check Eating / Hunger
        if (citizenState.contains("EAT") && citizen.getSaturation() >= 7.0D) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.DINING_SATISFIED, nearestPlayer, null, null);
        }
        if (citizen.getSaturation() < 3.0D || citizenState.contains("EAT") || explicitEventHint == EventType.SATURATION_LOW) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.HUNGRY, nearestPlayer, null, null);
        }

        // 6. Check Nearby Player Greeting (when player is within 6.5 blocks)
        if (explicitEventHint == EventType.GREETING || (nearestPlayerDistSqr <= 42.25D && world.random.nextFloat() < 0.35F)) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.PLAYER_GREETING, nearestPlayer, null, null);
        }

        // 7. Check Outdoor Thunderstorm or Rain Exposure (only when actually outdoors under open sky!)
        final boolean exposedToRain = world.isRaining() && world.canSeeSky(pos.above());
        if (exposedToRain && (explicitEventHint == EventType.BAD_WEATHER || world.random.nextFloat() < 0.45F)) {
            final CitizenVoiceContext weatherContext = world.isThundering()
                    ? CitizenVoiceContext.THUNDERSTORM
                    : CitizenVoiceContext.RAIN_AND_STORM;
            return new ResolvedVoiceSituation(weatherContext, nearestPlayer, null, null);
        }

        // 8. Check Missing Tools / Equipment / Waiting for Materials
        final boolean waitingForMaterials = citizen.isIdleAtJob()
                || workerState.contains("NEEDS")
                || workerState.contains("GATHERING_REQUIRED_MATERIALS")
                || explicitEventHint == EventType.MISSING_EQUIPMENT;
        if (waitingForMaterials && job != null && !isGuard) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.MISSING_TOOLS_OR_MATERIALS, nearestPlayer, null, null);
        }

        // 9. Discover nearby fellow citizens & patrolling guards for social/reassurance interactions
        String nearbyGuardName = null;
        String nearbyFriendName = null;
        if (entity != null) {
            final List<EntityCitizen> nearbyCitizens = world.getEntitiesOfClass(
                    EntityCitizen.class,
                    entity.getBoundingBox().inflate(11.0D),
                    other -> other != null && other != entity && other.isAlive());
            for (final EntityCitizen other : nearbyCitizens) {
                final boolean otherIsGuard = other.getCitizenJobHandler() != null
                        && other.getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard<?>;
                if (otherIsGuard && nearbyGuardName == null) {
                    nearbyGuardName = other.getName().getString();
                } else if (!otherIsGuard && nearbyFriendName == null) {
                    nearbyFriendName = other.getName().getString();
                }
            }
        }

        if (world.random.nextFloat() < 0.35F) {
            if (isGuard && nearbyFriendName != null) {
                return new ResolvedVoiceSituation(CitizenVoiceContext.GUARD_REASSURANCE, nearestPlayer, null, nearbyFriendName);
            } else if (!isGuard && nearbyGuardName != null) {
                return new ResolvedVoiceSituation(CitizenVoiceContext.WORKER_THANKS_GUARD, nearestPlayer, nearbyGuardName, nearbyFriendName);
            }
        }

        // 10. Check Home Building Comfort (ONLY when the citizen is actually inside or within 12 blocks of their home!)
        final IBuilding home = citizen.getHomeBuilding();
        if (home != null && home.getPosition().distSqr(pos) <= 144.0D && world.random.nextFloat() < 0.35F) {
            if (home.getBuildingLevelEquivalent() <= 2) {
                return new ResolvedVoiceSituation(CitizenVoiceContext.HOME_COMFORT_BAD, nearestPlayer, nearbyGuardName, nearbyFriendName);
            } else if (home.getBuildingLevelEquivalent() >= 4) {
                return new ResolvedVoiceSituation(CitizenVoiceContext.HOME_COMFORT_GOOD, nearestPlayer, nearbyGuardName, nearbyFriendName);
            }
        }

        // 11. Early-morning commute flavor (0..2200 ticks)
        final long dayTime = world.getDayTime() % 24000L;
        if (dayTime < 2200L && job != null && world.random.nextFloat() < 0.30F) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.MORNING_COMMUTE, nearestPlayer, nearbyGuardName, nearbyFriendName);
        }

        // 12. Citizen-to-citizen social chatter when passing or hanging out near another colonist
        if (nearbyFriendName != null && (citizenState.contains("LEISURE") || world.random.nextFloat() < 0.25F)) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.SOCIAL_CHATTER, nearestPlayer, nearbyGuardName, nearbyFriendName);
        }

        // 13. Active Job Work vs Leisure / Mood
        if (citizenState.contains("LEISURE")) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.LEISURE_AND_HAPPY, nearestPlayer, nearbyGuardName, nearbyFriendName);
        }

        if (job != null && (!workerState.isEmpty() && !workerState.equals("IDLE") || isGuard)) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.ACTIVE_JOB_WORK, nearestPlayer, nearbyGuardName, nearbyFriendName);
        }

        final double happiness = (citizen.getCitizenHappinessHandler() != null && colony != null)
                ? citizen.getCitizenHappinessHandler().getHappiness(colony, citizen)
                : 7.0D;
        if (happiness < 4.5D) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.UNHAPPY_MOOD, nearestPlayer, nearbyGuardName, nearbyFriendName);
        }

        return new ResolvedVoiceSituation(CitizenVoiceContext.LEISURE_AND_HAPPY, nearestPlayer, nearbyGuardName, nearbyFriendName);
    }

    /**
     * Plays the context-matched MineColonies/TownTalk audio event and broadcasts the floating 3D speech bubble.
     */
    private static void dispatchAudioAndSpeechBubble(
            @NotNull final ServerLevel world,
            @NotNull final BlockPos pos,
            @NotNull final ICitizenData citizen,
            @NotNull final ResolvedVoiceSituation situation) {

        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        if (cfg.enableSmartSoundEventRouting.get()) {
            INTERNAL_SOUND_DISPATCH.set(Boolean.TRUE);
            try {
                SoundUtils.playSoundAtCitizenWith(world, pos, situation.context().getSoundEventType(), citizen, 100.0D);
            } finally {
                INTERNAL_SOUND_DISPATCH.set(Boolean.FALSE);
            }
        }

        if (cfg.enableFloatingSpeechBubbles.get()) {
            broadcastSpeechBubble(world, pos, citizen, situation);
        }
    }

    /**
     * Generates the formatted English voiceline and sends a {@link CitizenVoicelinePayload} to nearby players.
     */
    private static void broadcastSpeechBubble(
            @NotNull final ServerLevel world,
            @NotNull final BlockPos pos,
            @NotNull final ICitizenData citizen,
            @NotNull final ResolvedVoiceSituation situation) {

        final AbstractCivilianEntity entity = citizen.getEntity().orElse(null);
        if (entity == null || !entity.isAlive()) {
            return;
        }

        final String rawTemplate = selectTemplate(world, citizen, situation.context());
        final String resolvedText = formatPlaceholders(world, citizen, situation, rawTemplate);
        if (resolvedText.isEmpty()) {
            return;
        }

        final String jobName = resolveJobDisplayName(citizen);
        final int durationSeconds = Math.max(2, Math.min(20,
                AdvancedColoniesConfig.CITIZEN_COMMON.speechBubbleDurationSeconds.get()));
        final CitizenVoicelinePayload payload = new CitizenVoicelinePayload(
                entity.getId(),
                citizen.getName(),
                jobName,
                resolvedText,
                situation.context().getAccentColorRgb(),
                durationSeconds * 20);

        final int maxRange = Math.max(8, Math.min(64,
                AdvancedColoniesConfig.CITIZEN_COMMON.voicelineMaxRangeBlocks.get()));
        final double maxRangeSqr = (double) maxRange * maxRange;

        for (final ServerPlayer player : world.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= maxRangeSqr) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @NotNull
    private static String selectTemplate(
            @NotNull final ServerLevel world,
            @NotNull final ICitizenData citizen,
            @NotNull final CitizenVoiceContext context) {

        final List<String> candidates = new ArrayList<>();
        final String jobKey = (citizen.getJob() != null
                && citizen.getJob().getJobRegistryEntry() != null
                && citizen.getJob().getJobRegistryEntry().getKey() != null)
                ? citizen.getJob().getJobRegistryEntry().getKey().getPath().toLowerCase(Locale.ROOT)
                : resolveJobDisplayName(citizen).toLowerCase(Locale.ROOT);

        // 1. Add built-in default or job-specific templates
        if (context == CitizenVoiceContext.ACTIVE_JOB_WORK && citizen.getJob() != null) {
            candidates.addAll(CitizenVoiceContext.getJobSpecificWorkTemplates(jobKey));
        } else {
            candidates.addAll(context.getDefaultTemplates());
        }

        // 2. Merge any custom templates from config (supports "CONTEXT:text" and "CONTEXT|jobKey|text")
        final String colonPrefix = context.name() + ":";
        final String pipePrefix = context.name() + "|";
        for (final String customEntry : AdvancedColoniesConfig.CITIZEN_COMMON.customVoicelineTemplates.get()) {
            if (customEntry == null) {
                continue;
            }
            if (customEntry.regionMatches(true, 0, colonPrefix, 0, colonPrefix.length())) {
                final String text = customEntry.substring(colonPrefix.length()).trim();
                if (!text.isEmpty()) {
                    candidates.add(text);
                }
            } else if (customEntry.regionMatches(true, 0, pipePrefix, 0, pipePrefix.length())) {
                final String remainder = customEntry.substring(pipePrefix.length());
                final int secondPipe = remainder.indexOf('|');
                if (secondPipe > 0) {
                    final String targetJob = remainder.substring(0, secondPipe).trim().toLowerCase(Locale.ROOT);
                    final String text = remainder.substring(secondPipe + 1).trim();
                    if (!text.isEmpty() && ("*".equals(targetJob) || jobKey.contains(targetJob))) {
                        candidates.add(text);
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return "";
        }

        final int colonyId = citizen.getColony() != null ? citizen.getColony().getID() : 0;
        final long citizenKey = (((long) colonyId) << 32) | (citizen.getId() & 0xFFFFFFFFL);
        final Integer lastIdx = CITIZEN_LAST_TEMPLATE_IDX.get(citizenKey);

        int chosenIdx = world.random.nextInt(candidates.size());
        if (candidates.size() > 1 && lastIdx != null && chosenIdx == lastIdx) {
            chosenIdx = (chosenIdx + 1) % candidates.size();
        }
        CITIZEN_LAST_TEMPLATE_IDX.put(citizenKey, chosenIdx);
        return candidates.get(chosenIdx);
    }

    @NotNull
    private static String formatPlaceholders(
            @NotNull final ServerLevel world,
            @NotNull final ICitizenData citizen,
            @NotNull final ResolvedVoiceSituation situation,
            @NotNull final String template) {

        final String citizenName = citizen.getName();
        final String jobName = resolveJobDisplayName(citizen);
        final String buildingName = resolveBuildingDisplayName(citizen);
        final int buildingLevel = resolveBuildingLevel(citizen);
        final String colonyName = (citizen.getColony() != null && citizen.getColony().getName() != null && !citizen.getColony().getName().isBlank())
                ? citizen.getColony().getName()
                : "our Colony";
        final String playerName = situation.nearestPlayer() != null
                ? situation.nearestPlayer().getName().getString()
                : "Mayor";
        final String guardName = situation.nearbyGuardName() != null
                ? situation.nearbyGuardName()
                : "the Guard";
        final String friendName = situation.nearbyFriendName() != null
                ? situation.nearbyFriendName()
                : "neighbor";
        final String weatherName = world.isThundering() ? "thunderstorm" : (world.isRaining() ? "rain" : "clear sky");
        final long dayTime = world.getDayTime() % 24000L;
        final String timeOfDay = dayTime < 6000L
                ? "morning"
                : (dayTime < 12000L ? "afternoon" : (dayTime < 13800L ? "evening" : "night"));

        return template
                .replace("{citizen}", citizenName)
                .replace("{job}", jobName)
                .replace("{building}", buildingName)
                .replace("{building_level}", String.valueOf(buildingLevel))
                .replace("{colony}", colonyName)
                .replace("{player}", playerName)
                .replace("{guard}", guardName)
                .replace("{friend}", friendName)
                .replace("{weather}", weatherName)
                .replace("{time}", timeOfDay);
    }

    @NotNull
    private static String resolveJobDisplayName(@NotNull final ICitizenData citizen) {
        final IJob<?> job = citizen.getJob();
        if (job == null || job.getJobRegistryEntry() == null) {
            return "Colonist";
        }
        if (job.getJobRegistryEntry().getTranslationKey() != null) {
            final String translated = Component.translatable(job.getJobRegistryEntry().getTranslationKey()).getString();
            if (!translated.isEmpty() && !translated.startsWith("com.minecolonies")) {
                return translated;
            }
        }
        if (job.getJobRegistryEntry().getKey() != null) {
            final String path = job.getJobRegistryEntry().getKey().getPath();
            if (!path.isEmpty()) {
                return Character.toUpperCase(path.charAt(0)) + path.substring(1);
            }
        }
        return "Worker";
    }

    @NotNull
    private static String resolveBuildingDisplayName(@NotNull final ICitizenData citizen) {
        final IBuilding workBuilding = citizen.getWorkBuilding();
        if (workBuilding != null) {
            final String customName = workBuilding.getCustomName();
            if (customName != null && !customName.isBlank()) {
                return customName;
            }
            final String schematicName = workBuilding.getSchematicName();
            if (schematicName != null && !schematicName.isBlank()) {
                return Character.toUpperCase(schematicName.charAt(0))
                        + schematicName.substring(1).replace('_', ' ');
            }
        }
        final IBuilding homeBuilding = citizen.getHomeBuilding();
        if (homeBuilding != null) {
            return "Residence";
        }
        return "Colony";
    }

    private static int resolveBuildingLevel(@NotNull final ICitizenData citizen) {
        final IBuilding workBuilding = citizen.getWorkBuilding();
        if (workBuilding != null && workBuilding.getBuildingLevel() > 0) {
            return workBuilding.getBuildingLevel();
        }
        final IBuilding homeBuilding = citizen.getHomeBuilding();
        if (homeBuilding != null && homeBuilding.getBuildingLevel() > 0) {
            return homeBuilding.getBuildingLevel();
        }
        return 1;
    }

    private record ResolvedVoiceSituation(
            @NotNull CitizenVoiceContext context,
            @Nullable Player nearestPlayer,
            @Nullable String nearbyGuardName,
            @Nullable String nearbyFriendName) {
    }
}
