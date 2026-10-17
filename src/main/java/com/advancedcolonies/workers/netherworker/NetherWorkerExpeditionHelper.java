package com.advancedcolonies.workers.netherworker;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import org.jetbrains.annotations.Nullable;

/**
 * Domain helper for Nether Miner expedition survival and reliability when
 * {@code enableEnhancedNetherWorkerAI} is enabled.
 */
public final class NetherWorkerExpeditionHelper {

    /** Minimum health floor maintained during simulated off-screen Nether expedition combat. */
    private static final float MIN_EXPEDITION_HEALTH_FLOOR = 6.0F;

    private NetherWorkerExpeditionHelper() {
    }

    /**
     * Ensures the Nether Miner survives simulated off-screen adventure token combat during
     * {@code EntityAIWorkNether.stayInNether()} when {@code enableEnhancedNetherWorkerAI} is enabled,
     * preventing random single-tick combat RNG from wiping the worker and their gathered Nether loot.
     *
     * @param worker the Nether Miner citizen entity
     */
    public static void ensureExpeditionSurvival(@Nullable final AbstractEntityCitizen worker) {
        if (!AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.enableEnhancedNetherWorkerAI.get()
                || worker == null) {
            return;
        }

        final float floor = Math.min(worker.getMaxHealth(), Math.max(MIN_EXPEDITION_HEALTH_FLOOR, worker.getMaxHealth() * 0.35F));
        if (worker.getHealth() < floor) {
            worker.setHealth(floor);
        }
    }
}
