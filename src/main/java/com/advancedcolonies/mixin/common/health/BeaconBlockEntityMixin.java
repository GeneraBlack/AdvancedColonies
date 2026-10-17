package com.advancedcolonies.mixin.common.health;

import com.advancedcolonies.common.health.CitizenHealthAndHospitalHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Thin Mixin hook on {@link BeaconBlockEntity} to extend active Beacon buffs to nearby
 * MineColonies citizens when {@code enableCitizenBeaconEffects} is enabled.
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {

    /**
     * Applies Beacon primary and secondary status effects to citizens within the Beacon's range.
     *
     * @param level           the world level
     * @param pos             the beacon position
     * @param beaconLevel     the pyramid level
     * @param primaryEffect   the primary effect holder
     * @param secondaryEffect the secondary effect holder
     * @param ci              callback info
     */
    @Inject(method = "applyEffects", at = @At("RETURN"))
    private static void advancedcolonies$applyBeaconEffectsToCitizens(
            final Level level,
            final BlockPos pos,
            final int beaconLevel,
            @Nullable final Holder<MobEffect> primaryEffect,
            @Nullable final Holder<MobEffect> secondaryEffect,
            final CallbackInfo ci) {
        CitizenHealthAndHospitalHelper.applyNearbyBeaconEffectsToCitizens(
                level, pos, beaconLevel, primaryEffect, secondaryEffect);
    }
}
