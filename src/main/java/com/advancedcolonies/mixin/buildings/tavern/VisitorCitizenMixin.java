package com.advancedcolonies.mixin.buildings.tavern;

import com.advancedcolonies.buildings.tavern.TavernRecruitmentHelper;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link VisitorCitizen} to allow direct right-click recruitment when holding
 * the visitor's recruitment cost item.
 */
@Mixin(value = VisitorCitizen.class, remap = false)
public abstract class VisitorCitizenMixin {

    /**
     * Checks if the player is holding the visitor's recruitment cost item and directly recruits
     * the visitor when {@code enableTavernDirectRecruitment} is enabled.
     *
     * @param player the interacting player
     * @param hand   the interaction hand
     * @param cir    callback info returnable
     */
    @Inject(method = "directPlayerInteraction", at = @At("HEAD"), cancellable = true)
    private void advancedcolonies$tryDirectRecruitVisitor(
            final Player player,
            final InteractionHand hand,
            final CallbackInfoReturnable<InteractionResult> cir) {
        final VisitorCitizen self = (VisitorCitizen) (Object) this;
        final InteractionResult result = TavernRecruitmentHelper.tryDirectRecruitWithHeldItem(self, player, hand);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
