package com.advancedcolonies.mixin.common.bee;

import com.advancedcolonies.workers.herder.BeeAIHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin on vanilla Minecraft's {@link Bee} to fix hive amnesia, wandering drift,
 * hive-entry deadzones, roof/corner getting-stuck bugs, campfire/water deaths,
 * and slow flower discovery/pollination.
 */
@Mixin(Bee.class)
public abstract class BeeMixin extends Animal {

    @Shadow
    @Nullable
    BlockPos hivePos;

    @Shadow
    int ticksWithoutNectarSinceExitingHive;

    @Shadow
    private int underWaterTicks;

    @Shadow
    private int timeSinceSting;

    @Shadow
    abstract boolean wantsToEnterHive();

    @Shadow
    public abstract boolean hasStung();

    @Shadow
    private void setHasStung(final boolean hasStung) {
    }

    protected BeeMixin(final EntityType<? extends Animal> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void advancedcolonies$onInit(
            final EntityType<? extends Bee> entityType,
            final Level level,
            final CallbackInfo ci) {
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.LAVA, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_OTHER, -1.0F);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, -1.0F);
    }

    @Inject(method = "isHiveValid", at = @At("HEAD"), cancellable = true)
    private void advancedcolonies$onIsHiveValid(final CallbackInfoReturnable<Boolean> cir) {
        if (BeeAIHelper.isEnhancedBeeAIEnabled()) {
            cir.setReturnValue(BeeAIHelper.isValidHiveBlock(this.level(), this.hivePos));
        }
    }

    @Inject(method = "isTooFarAway", at = @At("HEAD"), cancellable = true)
    private void advancedcolonies$onIsTooFarAway(
            final BlockPos pos,
            final CallbackInfoReturnable<Boolean> cir) {
        if (BeeAIHelper.isEnhancedBeeAIEnabled() && pos != null && pos.equals(this.hivePos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "pathfindRandomlyTowards", at = @At("HEAD"), cancellable = true)
    private void advancedcolonies$onPathfindRandomlyTowards(
            final BlockPos pos,
            final CallbackInfo ci) {
        if (pos != null && BeeAIHelper.smartPathfindTowards((Bee) (Object) this, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void advancedcolonies$onHurt(
            final DamageSource source,
            final float amount,
            final CallbackInfoReturnable<Boolean> cir) {
        if (BeeAIHelper.shouldCancelBeeDamage((Bee) (Object) this, source)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void advancedcolonies$onDoHurtTarget(
            final Entity entity,
            final CallbackInfoReturnable<Boolean> cir) {
        if (BeeAIHelper.shouldPreventBeeSting((Bee) (Object) this, entity)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void advancedcolonies$onCustomServerAiStepHead(final CallbackInfo ci) {
        if (BeeAIHelper.isHazardProtectionEnabled()) {
            this.underWaterTicks = 0;
            if (this.hasStung() && this.getPersistentData().getBoolean(BeeAIHelper.TAG_PENNED)) {
                this.timeSinceSting = 0;
                this.setHasStung(false);
            }
        }
    }

    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void advancedcolonies$onCustomServerAiStepReturn(final CallbackInfo ci) {
        BeeAIHelper.tickEnhancedBeeAI(
                (Bee) (Object) this,
                this.wantsToEnterHive(),
                this.ticksWithoutNectarSinceExitingHive);
    }
}
