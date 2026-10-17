package com.advancedcolonies.mixin.common.interaction;

import com.advancedcolonies.workers.herder.HerdingAndButcherHelper;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.items.ItemLargeBottle;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin on {@link ItemLargeBottle} removing the item cooldown on milking or water collection
 * when {@code disableLargeBottleCooldown} is enabled.
 */
@Mixin(value = ItemLargeBottle.class, remap = false)
public abstract class ItemLargeBottleMixin extends Item {

    protected ItemLargeBottleMixin(@NotNull final Item.Properties properties) {
        super(properties);
    }

    @Inject(method = "interactLivingEntity", at = @At("HEAD"), cancellable = true, remap = true)
    private void advancedColonies$onInteractLivingEntity(
            @NotNull final ItemStack stack,
            @NotNull final Player player,
            @NotNull final LivingEntity entity,
            @NotNull final InteractionHand hand,
            final CallbackInfoReturnable<InteractionResult> cir) {

        if (!HerdingAndButcherHelper.isLargeBottleCooldownDisabled() || (Object) this != ModItems.large_empty_bottle) {
            return;
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return;
        }

        if (entity instanceof Cow && !entity.isBaby()) {
            player.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
            if (!InventoryUtils.addItemStackToItemHandler(
                    new PlayerMainInvWrapper(player.getInventory()),
                    ModItems.large_milk_bottle.getDefaultInstance())) {
                player.drop(ModItems.large_milk_bottle.getDefaultInstance(), false);
            }
            stack.shrink(1);
            cir.setReturnValue(InteractionResult.SUCCESS);
        } else if (entity instanceof Goat goat && !entity.isBaby()) {
            player.playSound(goat.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_MILK : SoundEvents.GOAT_MILK, 1.0F, 1.0F);
            if (!InventoryUtils.addItemStackToItemHandler(
                    new PlayerMainInvWrapper(player.getInventory()),
                    ModItems.large_milk_bottle.getDefaultInstance())) {
                player.drop(ModItems.large_milk_bottle.getDefaultInstance(), false);
            }
            stack.shrink(1);
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = true)
    private void advancedColonies$onUse(
            @NotNull final Level level,
            @NotNull final Player player,
            @NotNull final InteractionHand hand,
            final CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {

        if (!HerdingAndButcherHelper.isLargeBottleCooldownDisabled() || (Object) this != ModItems.large_empty_bottle) {
            return;
        }

        final ItemStack itemStack = player.getItemInHand(hand);
        final BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            final BlockPos blockPos = hitResult.getBlockPos();
            if (!level.mayInteract(player, blockPos)) {
                cir.setReturnValue(InteractionResultHolder.pass(itemStack));
                return;
            }
            if (level.getFluidState(blockPos).is(FluidTags.WATER)) {
                level.playSound(player, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
                if (!InventoryUtils.addItemStackToItemHandler(
                        new PlayerMainInvWrapper(player.getInventory()),
                        ModItems.large_water_bottle.getDefaultInstance())) {
                    player.drop(ModItems.large_water_bottle.getDefaultInstance(), false);
                }
                itemStack.shrink(1);
                cir.setReturnValue(InteractionResultHolder.success(itemStack));
            }
        }
    }
}
