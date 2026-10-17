package com.advancedcolonies.mixin.buildings.tavern;

import com.advancedcolonies.buildings.tavern.TavernRecruitmentHelper;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Thin Mixin hook on {@link RecruitmentInteraction} to prevent paid Tavern recruits from
 * absconding with the player's items when {@code enableTavernDirectRecruitment} is enabled.
 */
@Mixin(value = RecruitmentInteraction.class, remap = false)
public abstract class RecruitmentInteractionMixin {

    /**
     * Redirects the {@code BAD_VISITOR_CHANCE} roll so paid recruits never run off when enabled.
     *
     * @param random the world random source
     * @param bound  the random bound ({@code 100})
     * @return {@code 100} (preventing absconding) when enabled, or the vanilla roll
     */
    @Redirect(
            method = "onServerResponseTriggered",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    )
    private int advancedcolonies$preventPaidRecruitAbsconding(final RandomSource random, final int bound) {
        if (TavernRecruitmentHelper.shouldPreventVisitorAbsconding()) {
            return bound;
        }
        return random.nextInt(bound);
    }
}
