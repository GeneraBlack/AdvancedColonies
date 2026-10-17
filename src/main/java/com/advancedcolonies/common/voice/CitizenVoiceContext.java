package com.advancedcolonies.common.voice;

import com.minecolonies.api.sounds.EventType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Defines all environment-reactive and situational contexts for citizen voicelines,
 * mapping each context to its corresponding MineColonies / TownTalk {@link EventType},
 * 3D speech bubble accent color, and default situational English templates.
 */
public enum CitizenVoiceContext {

    RAID_COMBAT(
            EventType.DANGER,
            0xFF5555,
            List.of(
                    "Raiders in the colony! Stay alert and protect the buildings!",
                    "Hostiles spotted nearby! Everyone get behind the Guards!",
                    "We're under attack! Hold the line around the {building}!",
                    "To arms! Don't let those raiders breach the perimeter!"
            )
    ),

    GUARD_RETREAT(
            EventType.DANGER,
            0xFFAA00,
            List.of(
                    "Taking heavy damage! Falling back to the {building} to recover!",
                    "I'm badly wounded—retreating to regroup and patch up!",
                    "Need a breather before I can get back into the fight!"
            )
    ),

    SICK_OR_INJURED(
            EventType.SICKNESS,
            0xFF55FF,
            List.of(
                    "Feeling feverish... I really need to see the Healer at the Hospital.",
                    "My head is spinning. Hope the Hospital has the medicine I need.",
                    "Coughing all morning... I shouldn't push myself too hard today."
            )
    ),

    MOURNING(
            EventType.UNHAPPY,
            0xAAAAAA,
            List.of(
                    "The colony feels so quiet after our loss... we won't forget them.",
                    "Paying my respects today. May they rest in peace.",
                    "Taking a moment to remember our fallen friend before heading back to the {building}."
            )
    ),

    SLEEPING_OR_BEDTIME(
            EventType.OFF_TO_BED,
            0x55FFFF,
            List.of(
                    "Long shift at the {building} today. Time to finally get some sleep.",
                    "The sun's down—heading home to rest up for tomorrow's work.",
                    "What a day... my bed is calling my name."
            )
    ),

    HUNGRY(
            EventType.SATURATION_LOW,
            0xFFFF55,
            List.of(
                    "My stomach is growling. Time to grab a bite at the Restaurant.",
                    "Hard work as a {job} really builds up an appetite!",
                    "Can't focus on an empty stomach—need to find some food soon."
            )
    ),

    DINING_SATISFIED(
            EventType.SATURATION_HIGH,
            0x55FF55,
            List.of(
                    "Nothing beats a warm, hearty meal after working at the {building}!",
                    "Compliments to the Cook! That meal hit the spot.",
                    "Well fed and ready to get back to my duties as {job}!"
            )
    ),

    RAIN_AND_STORM(
            EventType.BAD_WEATHER,
            0x55AAFF,
            List.of(
                    "Working out in this {weather} is soaking my clothes right through!",
                    "Looks like this {weather} isn't letting up anytime soon.",
                    "Wish I had a dry roof over my head while this {weather} passes."
            )
    ),

    MISSING_TOOLS_OR_MATERIALS(
            EventType.MISSING_EQUIPMENT,
            0xFFAA00,
            List.of(
                    "Waiting on fresh supplies at the {building} before I can continue.",
                    "I could really use the right tools here at the {building}.",
                    "Hopefully the Courier brings the requested materials to the {building} soon."
            )
    ),

    HOME_COMFORT_GOOD(
            EventType.GOOD_HOUSING,
            0x55FF55,
            List.of(
                    "It feels wonderful having such a spacious, comfortable home to return to!",
                    "Our residences have really improved—living in this colony is a joy.",
                    "Home sweet home! A cozy place makes all the hard work worthwhile."
            )
    ),

    HOME_COMFORT_BAD(
            EventType.BAD_HOUSING,
            0xFFAA00,
            List.of(
                    "My residence is getting a bit cramped... hopefully the Builder upgrades it soon.",
                    "A slightly bigger house would make resting after work much nicer.",
                    "It's a humble roof over my head, though an upgrade wouldn't hurt."
            )
    ),

    GUARD_REASSURANCE(
            EventType.INTERACTION,
            0x55FFFF,
            List.of(
                    "Sector around the {building} is all clear. Carry on safely!",
                    "Keeping watch over this district—no monsters getting past me.",
                    "Patrol check complete near the {building}. Everything looks secure."
            )
    ),

    WORKER_THANKS_GUARD(
            EventType.INTERACTION,
            0x55FF55,
            List.of(
                    "Good to see {guard} patrolling nearby. Makes working at the {building} feel much safer!",
                    "Thanks for keeping watch over us, {guard}!",
                    "With {guard} on patrol in this sector, I can focus completely on my work."
            )
    ),

    PLAYER_GREETING(
            EventType.GREETING,
            0x55FF55,
            List.of(
                    "Good {time}, {player}! Everything at the {building} is coming along well.",
                    "Hello there, {player}! Always glad to see you checking in on us.",
                    "Greetings, {player}! Just tending to my duties as {job} at the {building}."
            )
    ),

    ACTIVE_JOB_WORK(
            EventType.SUCCESS,
            0xFFFFFF,
            List.of(
                    "Making steady progress at the {building} this {time}.",
                    "Another productive shift as {job} for the colony!",
                    "Keeping the {building} running smoothly today."
            )
    ),

    LEISURE_AND_HAPPY(
            EventType.HAPPY,
            0x55FF55,
            List.of(
                    "Taking a short breather to enjoy the atmosphere around the colony.",
                    "It's inspiring to see how much our colony has grown!",
                    "A fine {time} to catch my breath before the next task."
            )
    ),

    UNHAPPY_MOOD(
            EventType.UNHAPPY,
            0xFFFF55,
            List.of(
                    "Been a rough few days lately... hope things look up for the colony soon.",
                    "Feeling a little worn out today, but I'll keep doing my part.",
                    "Could use a bit of good news around the colony."
            )
    );

    private final EventType soundEventType;
    private final int accentColorRgb;
    private final List<String> defaultTemplates;

    CitizenVoiceContext(
            @NotNull final EventType soundEventType,
            final int accentColorRgb,
            @NotNull final List<String> defaultTemplates) {
        this.soundEventType = soundEventType;
        this.accentColorRgb = accentColorRgb;
        this.defaultTemplates = defaultTemplates;
    }

    @NotNull
    public EventType getSoundEventType() {
        return soundEventType;
    }

    public int getAccentColorRgb() {
        return accentColorRgb;
    }

    @NotNull
    public List<String> getDefaultTemplates() {
        return defaultTemplates;
    }

    /**
     * Returns job-specialized English work templates for {@link #ACTIVE_JOB_WORK} based on the citizen's job path.
     *
     * @param jobKey the lowercase job identifier or display name
     * @return job-specific templates, or {@link #defaultTemplates} if generic
     */
    @NotNull
    public static List<String> getJobSpecificWorkTemplates(@NotNull final String jobKey) {
        final String key = jobKey.toLowerCase(Locale.ROOT);
        if (key.contains("builder")) {
            return List.of(
                    "Measuring twice and placing once—this structure is really taking shape!",
                    "Brick by brick, another project for the colony comes together.",
                    "Clearing the site and laying foundations—a Builder's work is never done!"
            );
        }
        if (key.contains("farmer") || key.contains("planter")) {
            return List.of(
                    "The soil looks fertile today—this harvest should fill the Warehouse nicely!",
                    "Tending the fields and replanting fresh seeds while the {time} light lasts.",
                    "A healthy crop rotation keeps the whole colony well fed."
            );
        }
        if (key.contains("miner") || key.contains("quarrier")) {
            return List.of(
                    "The rock strata down here are tough, but the ore veins are worth every swing!",
                    "Reinforcing the shaft timbers and digging deeper for the colony.",
                    "Plenty of stone and ore heading up from the {building} today."
            );
        }
        if (key.contains("lumberjack") || key.contains("forester")) {
            return List.of(
                    "Felling timber and replanting saplings so the forest stays lush.",
                    "Fresh logs ready for the Sawmill and Builders!",
                    "Nothing like the sound of a clean axe swing in the {time}."
            );
        }
        if (key.contains("deliveryman") || key.contains("courier")) {
            return List.of(
                    "Special delivery! Keeping the supplies flowing between the Warehouse and huts.",
                    "So many requests today, but my routes are faster than ever!",
                    "Sorting crates and running batches—the lifeblood of colony logistics."
            );
        }
        if (key.contains("cook") || key.contains("chef") || key.contains("baker")) {
            return List.of(
                    "The ovens are hot and fresh meals are coming right up at the {building}!",
                    "Keeping the tables stocked so nobody in the colony works hungry.",
                    "Smells delicious in the {building} today!"
            );
        }
        if (key.contains("healer")) {
            return List.of(
                    "Checking on patients and organizing remedies at the Hospital.",
                    "Always ready with herbs and bandages whenever a colonist needs care.",
                    "A healthy colony is a productive colony!"
            );
        }
        if (key.contains("research") || key.contains("university")) {
            return List.of(
                    "Cross-referencing these ancient notes... we're close to a new discovery!",
                    "Every chapter studied at the University brings new knowledge to our people.",
                    "Fascinating findings in the library stacks this {time}."
            );
        }
        if (key.contains("enchanter")) {
            return List.of(
                    "Channeling arcane mana into fresh tomes at the {building}.",
                    "The enchantments are resonating strongly today!",
                    "Gathering experience and inscribing enchanted books for our workers."
            );
        }
        if (key.contains("concrete")) {
            return List.of(
                    "Mixing and curing batches of concrete—strong material for our Builders!",
                    "Powder in the water, solid blocks out—batch work makes it fly by."
            );
        }
        if (key.contains("cowboy") || key.contains("herder") || key.contains("shepherd") || key.contains("swine") || key.contains("chicken") || key.contains("beekeeper")) {
            return List.of(
                    "The animals at the {building} are healthy and well cared for today.",
                    "Fresh produce and stock from the pens—the herds are thriving!",
                    "Keeping the pens clean and the animals fed this {time}."
            );
        }
        if (key.contains("undertaker")) {
            return List.of(
                    "Tending the Graveyard with care and watching over our colonists.",
                    "Keeping the grounds peaceful and solemn at the {building}."
            );
        }
        if (key.contains("guard") || key.contains("knight") || key.contains("ranger") || key.contains("druid")) {
            return List.of(
                    "Eyes sharp, weapons ready—keeping my assigned patrol sector secure.",
                    "Scanning the horizon from my patrol route. All quiet so far.",
                    "Standing watch so the rest of the colony can work in peace."
            );
        }
        return ACTIVE_JOB_WORK.defaultTemplates;
    }
}
