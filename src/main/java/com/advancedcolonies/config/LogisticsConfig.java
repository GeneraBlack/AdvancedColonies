package com.advancedcolonies.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration settings for colony logistics, deliveryman batching,
 * warehouse dining, direct hut pickup, and precise minimum stock.
 */
public final class LogisticsConfig {

    public final ModConfigSpec.BooleanValue enableEnhancedDeliverymanAI;
    public final ModConfigSpec.BooleanValue deliverymanEatAtWarehouse;
    public final ModConfigSpec.BooleanValue pickMaterialsAtHutBlock;
    public final ModConfigSpec.BooleanValue enableUniversalMinimumStockModule;
    public final ModConfigSpec.BooleanValue enablePreciseMinimumStock;
    public final ModConfigSpec.BooleanValue enableUiQuickNavigation;

    public LogisticsConfig(final ModConfigSpec.Builder builder) {
        builder.comment("Logistics, Deliveryman & Minimum Stock Settings").push("logistics");

        this.enableEnhancedDeliverymanAI = builder
                .comment("Enables continuous multi-request pickup and delivery batching for couriers.")
                .define("enableEnhancedDeliverymanAI", true);

        this.deliverymanEatAtWarehouse = builder
                .comment("Allows couriers to eat directly at the Warehouse (using a Warehouse food menu) instead of walking to a restaurant.")
                .define("deliverymanEatAtWarehouse", true);

        this.pickMaterialsAtHutBlock = builder
                .comment("Non-courier workers pick up required items directly at their hut block instead of walking to individual racks.")
                .define("pickMaterialsAtHutBlock", true);

        this.enableUniversalMinimumStockModule = builder
                .comment("Adds the Minimum Stock module to worker huts that do not have one in vanilla MineColonies.")
                .define("enableUniversalMinimumStockModule", true);

        this.enablePreciseMinimumStock = builder
                .comment("Configures Minimum Stock in exact item counts rather than full 64-item stacks.")
                .define("enablePreciseMinimumStock", true);

        this.enableUiQuickNavigation = builder
                .comment("Adds quick-jump buttons between Minimum Stock / Menu / Recipe windows and the PostBox / Hut Inventory.")
                .define("enableUiQuickNavigation", true);

        builder.pop();
    }
}
