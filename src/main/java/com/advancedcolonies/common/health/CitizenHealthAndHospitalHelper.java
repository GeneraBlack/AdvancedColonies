package com.advancedcolonies.common.health;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.minecolonies.api.util.constant.CitizenConstants.LOW_SATURATION;

/**
 * Provides health, hospital, beacon status effect, and damage alert enhancements for citizens.
 *
 * <p>Controlled via {@link AdvancedColoniesConfig#CITIZEN_COMMON}:
 * <ul>
 *   <li>{@code enableEnhancedHospitalAI}, {@code seekDoctorHealthRatio}, {@code seekDoctorAbsoluteHealth}</li>
 *   <li>{@code enableUnhandledPatientSelfHeal}, {@code selfHealWaitSeconds}</li>
 *   <li>{@code enableCitizenBeaconEffects}</li>
 *   <li>{@code alertOnCitizenDamage}</li>
 * </ul>
 */
public final class CitizenHealthAndHospitalHelper {

    /** Minimum interval in game ticks between damage chat alerts for the same citizen (10 seconds). */
    private static final long DAMAGE_ALERT_COOLDOWN_TICKS = 200L;

    /** Tracks the last game tick at which a damage alert was broadcast for a given citizen key. */
    private static final Map<String, Long> LAST_DAMAGE_ALERT_TICK = new ConcurrentHashMap<>();

    private CitizenHealthAndHospitalHelper() {
    }

    /**
     * Evaluates whether a citizen is considered hurt enough to seek medical treatment at a Hospital.
     *
     * @param citizenData    the citizen data
     * @param vanillaIsHurt  the original vanilla result
     * @return {@code true} if the citizen should seek a doctor
     */
    public static boolean evaluateIsHurt(final ICitizenData citizenData, final boolean vanillaIsHurt) {
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedHospitalAI.get()) {
            return vanillaIsHurt;
        }
        if (citizenData == null || citizenData.getEntity().isEmpty()) {
            return false;
        }
        if (citizenData.getJob() instanceof AbstractJobGuard) {
            return false;
        }
        if (citizenData.getSaturation() <= LOW_SATURATION) {
            return false;
        }

        final AbstractEntityCitizen entity = citizenData.getEntity().get();
        final float health = entity.getHealth();
        final float maxHealth = Math.max(1.0f, entity.getMaxHealth());

        final double absThreshold = AdvancedColoniesConfig.CITIZEN_COMMON.seekDoctorAbsoluteHealth.get();
        final double ratioThreshold = AdvancedColoniesConfig.CITIZEN_COMMON.seekDoctorHealthRatio.get();

        return health < absThreshold || (health / maxHealth) < ratioThreshold;
    }

    /**
     * Evaluates whether a disease-free citizen is healthy enough to leave the {@code SICK} state.
     *
     * @param citizen the citizen entity
     * @return {@code true} if the citizen no longer needs a doctor
     */
    public static boolean isRecoveredFromInjury(final EntityCitizen citizen) {
        if (citizen == null) {
            return true;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedHospitalAI.get()) {
            return citizen.getHealth() > 6.0f;
        }
        final float health = citizen.getHealth();
        final float maxHealth = Math.max(1.0f, citizen.getMaxHealth());
        final double absThreshold = AdvancedColoniesConfig.CITIZEN_COMMON.seekDoctorAbsoluteHealth.get();
        final double ratioThreshold = AdvancedColoniesConfig.CITIZEN_COMMON.seekDoctorHealthRatio.get();
        return health >= absThreshold && (health / maxHealth) >= ratioThreshold;
    }

    /**
     * Returns the random denominator used in {@code EntityAISickTask.waitForCure()} for spontaneous
     * patient recovery when a Healer has not cured the patient in time.
     *
     * @param vanillaBound the vanilla random bound ({@code 60 * 60 * 2 = 7200})
     * @return the configured bound in seconds (since {@code waitForCure} ticks once per second)
     */
    public static int getPatientSelfHealRandomBound(final int vanillaBound) {
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableUnhandledPatientSelfHeal.get()) {
            return vanillaBound;
        }
        return Math.max(10, AdvancedColoniesConfig.CITIZEN_COMMON.selfHealWaitSeconds.get());
    }

    /**
     * Applies active vanilla Beacon status effects to all MineColonies citizens within the Beacon's range.
     *
     * @param level           the world level
     * @param pos             the beacon block position
     * @param beaconLevel     the beacon pyramid level (1..4)
     * @param primaryEffect   the primary status effect holder
     * @param secondaryEffect the secondary status effect holder
     */
    public static void applyNearbyBeaconEffectsToCitizens(
            final Level level,
            final BlockPos pos,
            final int beaconLevel,
            final Holder<MobEffect> primaryEffect,
            final Holder<MobEffect> secondaryEffect) {
        if (level == null || level.isClientSide() || primaryEffect == null) {
            return;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableCitizenBeaconEffects.get()) {
            return;
        }

        final double range = beaconLevel * 10 + 10;
        int amplifier = 0;
        if (beaconLevel >= 4 && Objects.equals(primaryEffect, secondaryEffect)) {
            amplifier = 1;
        }

        final int durationTicks = (9 + beaconLevel * 2) * 20;
        final AABB box = new AABB(pos).inflate(range).expandTowards(0.0D, level.getHeight(), 0.0D);
        final List<AbstractEntityCitizen> citizens = level.getEntitiesOfClass(AbstractEntityCitizen.class, box);

        for (final AbstractEntityCitizen citizen : citizens) {
            citizen.addEffect(new MobEffectInstance(primaryEffect, durationTicks, amplifier, true, true));
            if (beaconLevel >= 4 && !Objects.equals(primaryEffect, secondaryEffect) && secondaryEffect != null) {
                citizen.addEffect(new MobEffectInstance(secondaryEffect, durationTicks, 0, true, true));
            } else if (beaconLevel >= 4 && secondaryEffect == null) {
                citizen.addEffect(new MobEffectInstance(MobEffects.REGENERATION, durationTicks, 0, true, true));
            }
        }
    }

    /**
     * Broadcasts a colony alert message when a non-guard citizen takes significant damage.
     *
     * @param citizen      the hurt citizen
     * @param damageSource the damage source
     * @param damage       the damage amount dealt
     */
    public static void notifyColonyOnCitizenDamage(
            final EntityCitizen citizen,
            final DamageSource damageSource,
            final float damage) {
        if (citizen == null || citizen.level().isClientSide() || damage < 1.0f) {
            return;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.alertOnCitizenDamage.get()) {
            return;
        }

        final ICitizenData data = citizen.getCitizenData();
        if (data == null || data.getJob() instanceof AbstractJobGuard) {
            return;
        }

        final IColony colony = data.getColony();
        if (colony == null) {
            return;
        }

        final String key = colony.getID() + ":" + data.getId();
        final long now = citizen.level().getGameTime();
        final Long lastTick = LAST_DAMAGE_ALERT_TICK.get(key);
        if (lastTick != null && (now - lastTick) < DAMAGE_ALERT_COOLDOWN_TICKS) {
            return;
        }
        LAST_DAMAGE_ALERT_TICK.put(key, now);

        final Entity attacker = damageSource.getEntity();
        final String causeName = attacker != null
                ? attacker.getDisplayName().getString()
                : damageSource.getMsgId();
        final BlockPos pos = citizen.blockPosition();

        MessageUtils.format(
                "advancedcolonies.message.citizen_hurt_alert",
                data.getName(),
                causeName,
                pos.getX(),
                pos.getY(),
                pos.getZ())
                .withPriority(MessageUtils.MessagePriority.DANGER)
                .sendTo(colony)
                .forManagers();
    }
}
