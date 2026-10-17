package com.advancedcolonies;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.network.AdvancedColoniesNetwork;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Main entrypoint for the Advanced Colonies mod.
 * Registers the modular configuration specification and initializes mod subsystems.
 */
@Mod(AdvancedColonies.MOD_ID)
public final class AdvancedColonies {

    /** Unique Mod Identifier matching neoforge.mods.toml and gradle.properties. */
    public static final String MOD_ID = "advancedcolonies";

    /** Shared mod logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    public AdvancedColonies(final IEventBus modEventBus, final ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, AdvancedColoniesConfig.SPEC, MOD_ID + "-common.toml");
        modEventBus.addListener(AdvancedColoniesNetwork::onRegisterPayloads);
        LOGGER.info("Advanced Colonies initialized with modular configuration and network debug sync.");
    }
}
