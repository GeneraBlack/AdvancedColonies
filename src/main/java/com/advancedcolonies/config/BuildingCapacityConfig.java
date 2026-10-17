package com.advancedcolonies.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration settings for scaling worker slots, warehouse couriers,
 * and residential beds per building level.
 */
public final class BuildingCapacityConfig {

    public final ModConfigSpec.BooleanValue extraRestaurantCooks;
    public final ModConfigSpec.BooleanValue extraCookhouseChefs;
    public final ModConfigSpec.BooleanValue extraHospitalHealers;
    public final ModConfigSpec.BooleanValue extraStoneSmelteryWorkers;
    public final ModConfigSpec.BooleanValue extraDeliverymanHutWorkers;

    public final ModConfigSpec.BooleanValue scaleWarehouseCouriers;
    public final ModConfigSpec.DoubleValue warehouseCourierMultiplier;

    public final ModConfigSpec.IntValue residentialBedsPerLevel;

    public BuildingCapacityConfig(final ModConfigSpec.Builder builder) {
        builder.comment("Building Worker & Bed Capacity Scaling").push("buildingCapacity");

        this.extraRestaurantCooks = builder
                .comment("Scales Restaurant worker capacity up to 3 cooks/waiters at building level 5.")
                .define("extraRestaurantCooks", true);

        this.extraCookhouseChefs = builder
                .comment("Scales Cookhouse (Kitchen) worker capacity up to 3 chefs at building level 5.")
                .define("extraCookhouseChefs", true);

        this.extraHospitalHealers = builder
                .comment("Scales Hospital worker capacity up to 3 healers at building level 5.")
                .define("extraHospitalHealers", true);

        this.extraStoneSmelteryWorkers = builder
                .comment("Scales Stone Smeltery worker capacity up to 3 workers at building level 5.")
                .define("extraStoneSmelteryWorkers", true);

        this.extraDeliverymanHutWorkers = builder
                .comment("Scales Deliveryman Hut capacity up to 3 couriers per hut at building level 5.")
                .define("extraDeliverymanHutWorkers", true);

        this.scaleWarehouseCouriers = builder
                .comment("Scales maximum couriers assigned to a Warehouse using level * (level + 1) * multiplier.")
                .define("scaleWarehouseCouriers", true);

        this.warehouseCourierMultiplier = builder
                .comment("Multiplier applied to the Warehouse courier capacity formula.")
                .defineInRange("warehouseCourierMultiplier", 1.0D, 0.25D, 5.0D);

        this.residentialBedsPerLevel = builder
                .comment("Number of citizen beds per Residence level (1 = vanilla 5 beds at lvl 5; 3 = 15 beds at lvl 5).")
                .defineInRange("residentialBedsPerLevel", 1, 1, 5);

        builder.pop();
    }
}
