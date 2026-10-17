package com.advancedcolonies.buildings.graveyard;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IGraveData;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.network.messages.client.VanillaParticleMessage;
import com.minecolonies.core.tileentities.TileEntityGrave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import static com.minecolonies.api.util.constant.StatisticsConstants.CITIZENS_RESURRECTED;
import static com.minecolonies.api.util.constant.TranslationConstants.MESSAGE_INFO_CITIZEN_UNDERTAKER_RESURRECTED_SUCCESS;

/**
 * Enables manual player-triggered resurrection of fallen citizens at a MineColonies grave
 * using a configurable catalyst item (default: {@code minecraft:totem_of_undying}).
 *
 * <p>Controlled via {@link AdvancedColoniesConfig#CITIZEN_COMMON}:
 * <ul>
 *   <li>{@code enableGraveyardResurrection}</li>
 *   <li>{@code resurrectionItemId}</li>
 *   <li>{@code resurrectionSuccessChance}</li>
 * </ul>
 */
public final class GraveyardResurrectionHelper {

    private GraveyardResurrectionHelper() {
    }

    /**
     * Attempts to resurrect a fallen citizen from a {@link TileEntityGrave} when a player right-clicks
     * the grave block with the configured catalyst item.
     *
     * @param stack  the held item stack
     * @param level  the world level
     * @param pos    the grave block position
     * @param player the interacting player
     * @param hand   the interaction hand
     * @return {@link ItemInteractionResult#SUCCESS} if handled, or {@code null} to proceed with normal grave opening
     */
    public static ItemInteractionResult tryManualGraveResurrection(
            final ItemStack stack,
            final Level level,
            final BlockPos pos,
            final Player player,
            final InteractionHand hand) {
        if (stack == null || stack.isEmpty() || level == null || pos == null || player == null) {
            return null;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableGraveyardResurrection.get()) {
            return null;
        }

        final String configuredItemId = AdvancedColoniesConfig.CITIZEN_COMMON.resurrectionItemId.get();
        final ResourceLocation itemKey = ResourceLocation.tryParse(configuredItemId);
        if (itemKey == null || !BuiltInRegistries.ITEM.containsKey(itemKey)) {
            return null;
        }

        final Item requiredItem = BuiltInRegistries.ITEM.get(itemKey);
        if (!stack.is(requiredItem)) {
            return null;
        }

        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof TileEntityGrave graveTile)) {
            return null;
        }

        final IGraveData graveData = graveTile.getGraveData();
        if (graveData == null || graveData.getCitizenDataNBT() == null) {
            return null;
        }

        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null || !colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS)) {
            return null;
        }

        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        if (!player.isCreative()) {
            stack.shrink(1);
            player.setItemInHand(hand, stack);
        }

        level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        new VanillaParticleMessage(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, ParticleTypes.TOTEM_OF_UNDYING)
                .sendToTargetPoint((net.minecraft.server.level.ServerLevel) level, null, pos.getX(), pos.getY(), pos.getZ(), 32);

        final double chance = AdvancedColoniesConfig.CITIZEN_COMMON.resurrectionSuccessChance.get();
        if (level.getRandom().nextDouble() <= chance) {
            new VanillaParticleMessage(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, ParticleTypes.HEART)
                    .sendToTargetPoint((net.minecraft.server.level.ServerLevel) level, null, pos.getX(), pos.getY(), pos.getZ(), 32);

            final ICitizenData resurrected = colony.getCitizenManager()
                    .resurrectCivilianData(graveData.getCitizenDataNBT(), true, level, pos);
            if (resurrected != null) {
                if (AdvancedColoniesConfig.CITIZEN_COMMON.returnGearOnUndertakerResurrect.get()
                        && resurrected.getInventory() != null) {
                    InventoryUtils.transferAllItemHandler(graveTile.getInventory(), resurrected.getInventory());
                }
                MessageUtils.format(MESSAGE_INFO_CITIZEN_UNDERTAKER_RESURRECTED_SUCCESS, resurrected.getName())
                        .sendTo(colony)
                        .forManagers();
                colony.getStatisticsManager().increment(CITIZENS_RESURRECTED, colony.getDay());
                colony.getCitizenManager().updateCitizenMourn(resurrected, false);
            }

            // Drop any remaining items in the grave before removing the block
            InventoryUtils.dropItemHandler(graveTile.getInventory(), level, pos.getX(), pos.getY(), pos.getZ());

            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }

        return ItemInteractionResult.SUCCESS;
    }

    /**
     * Transfers the fallen citizen's items (which the Undertaker emptied from the grave into their
     * own inventory during {@code EMPTY_GRAVE}) directly back into the resurrected citizen's inventory
     * during {@code EntityAIWorkUndertaker#tryResurrect()}, while keeping the Undertaker's own shovel
     * and any Totem of Undying.
     *
     * @param undertaker  the Undertaker worker entity
     * @param resurrected the newly resurrected citizen data
     * @param shovelSlot  the slot index of the Undertaker's own shovel (to preserve)
     */
    public static void returnUndertakerGraveItemsToCitizen(
            final com.minecolonies.api.entity.citizen.AbstractEntityCitizen undertaker,
            final ICitizenData resurrected,
            final int shovelSlot) {
        if (undertaker == null || resurrected == null || resurrected.getInventory() == null) {
            return;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.returnGearOnUndertakerResurrect.get()) {
            return;
        }

        final com.minecolonies.api.inventory.InventoryCitizen undertakerInv = undertaker.getInventoryCitizen();
        final com.minecolonies.api.inventory.InventoryCitizen citizenInv = resurrected.getInventory();
        if (undertakerInv == null) {
            return;
        }

        for (int slot = 0; slot < undertakerInv.getSlots(); slot++) {
            if (slot == shovelSlot) {
                continue;
            }
            final ItemStack stackInSlot = undertakerInv.getStackInSlot(slot);
            if (stackInSlot.isEmpty() || stackInSlot.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) {
                continue;
            }
            InventoryUtils.transferItemStackIntoNextFreeSlotInItemHandler(undertakerInv, slot, citizenInv);
        }
    }
}
