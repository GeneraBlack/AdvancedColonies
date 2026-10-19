package com.advancedcolonies.common.voice;

import com.minecolonies.api.sounds.EventType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Defines all environment-reactive and situational contexts for citizen voicelines,
 * mapping each context to its corresponding MineColonies / TownTalk {@link EventType},
 * 3D speech bubble accent color, and an extensive library of natural English lines.
 */
public enum CitizenVoiceContext {

    GUARD_COMBAT(
            EventType.DANGER,
            0xFF4444,
            List.of(
                    "Steel up! Keep those hostiles away from the {building}!",
                    "Hold the perimeter! Nobody gets past the Guard of {colony}!",
                    "Engaging the enemy! Civilians, fall back behind me!",
                    "For {colony}! Let's drive these raiders back where they came from!",
                    "Target in sight—focus fire and protect the {building}!",
                    "Shields high! Don't let them flank our position!",
                    "I'll draw their attention—everyone else get to safety!",
                    "Not on my watch! {colony} stands strong!",
                    "Pushing the hostiles back from the {building}—stay sharp!",
                    "Another wave incoming! Stand firm and watch each other's backs!",
                    "You picked the wrong colony to raid today!",
                    "Clearing the street near the {building}—keep your heads down!"
            )
    ),

    RAID_COMBAT(
            EventType.DANGER,
            0xFF5555,
            List.of(
                    "Raiders in {colony}! Everyone get inside and lock the doors!",
                    "Hostiles spotted near the {building}! Somebody call {guard}!",
                    "We're under attack! Get behind the Guards, hurry!",
                    "Sound the alarm! Monsters are closing in on the {building}!",
                    "It's not safe out here—head for shelter until the Guards clear the area!",
                    "Watch out! There's fighting breaking out right near the {building}!",
                    "Stay away from the perimeter! Let the Guards handle them!",
                    "By the bells, raiders! Please hold the line, {guard}!",
                    "Drop the tools and take cover! {colony} is under attack!",
                    "Keep your head down and stay close to the {building} until the all-clear!",
                    "I saw blades gleaming outside town—guards, over here!",
                    "Don't panic, just get off the main road and let the patrol through!"
            )
    ),

    GUARD_RETREAT(
            EventType.DANGER,
            0xFFAA00,
            List.of(
                    "Taking heavy hits! Falling back to the {building} to patch up!",
                    "Armor's battered and I'm bleeding—retreating to regroup!",
                    "Can't protect {colony} if I drop here... pulling back for a breather!",
                    "Too many of them at once! Disengaging to recover my strength!",
                    "Need a moment to bind these wounds before I jump back into the fray!",
                    "Falling back to a safer position—hold on, {colony}, I'll be right back!",
                    "That last blow nearly took me out... retreating to catch my breath!",
                    "Tactical retreat to the {building}! Just need a quick recovery!",
                    "My shield arm's numb—stepping back from the front line for a second!",
                    "Wounded badly! Heading back to safety before they surround me!"
            )
    ),

    SICK_OR_INJURED(
            EventType.SICKNESS,
            0xFF55FF,
            List.of(
                    "Feeling feverish all morning... I really need to see the Healer at the Hospital.",
                    "My head is spinning. Hope the Hospital has the medicine I need.",
                    "Coughing non-stop today... I shouldn't push myself too hard at the {building}.",
                    "Can barely keep my eyes open with this chill. Off to the Hospital I go.",
                    "My throat feels like sandpaper... hopefully the Healer has some herbal tea.",
                    "Every step feels heavy today. Definitely caught whatever's going around {colony}.",
                    "Better get checked out at the Hospital before I pass this cold to {friend}.",
                    "Shivering even in my cloak... yeah, I need a Healer.",
                    "Not feeling like myself at all today. Time to rest up and get treated.",
                    "Hope the Courier delivered those medical herbs to the Hospital already...",
                    "Sorry, {player}, I'm a bit under the weather today—heading for treatment.",
                    "A quick visit to the Healer and I'll be back at the {building} in no time."
            )
    ),

    MOURNING(
            EventType.UNHAPPY,
            0xAAAAAA,
            List.of(
                    "{colony} feels so quiet after our loss... we won't forget them.",
                    "Paying my respects at the Graveyard today. May they rest in peace.",
                    "Taking a quiet moment to remember our fallen friend before heading back to the {building}.",
                    "It's hard walking past their old post without feeling the loss.",
                    "Leaving a flower by the grave... they gave so much to {colony}.",
                    "We'll honor their memory by keeping {colony} strong and safe.",
                    "Still can't believe they're gone. Life out here can be so unforgiving.",
                    "Sharing a silent prayer with {friend} for those we've lost.",
                    "The whole town has a heavy heart today. Rest easy, old friend.",
                    "They helped build {colony} into what it is today. We owe them everything.",
                    "Just needed a few quiet minutes by the headstones to say goodbye.",
                    "Even the wind through {colony} sounds a little sadder this {time}."
            )
    ),

    SLEEPING_OR_BEDTIME(
            EventType.OFF_TO_BED,
            0x55FFFF,
            List.of(
                    "Long shift at the {building} today. Time to finally get some sleep.",
                    "The sun's down and the lanterns are lit—heading home to rest up for tomorrow.",
                    "What a day... my bed is definitely calling my name.",
                    "Feet are aching and my eyelids are heavy. Goodnight, {colony}!",
                    "Locking up the {building} for the night. Can't wait to hit the pillow.",
                    "Another full day's work behind me. Time for some well-earned rest.",
                    "Yawn... the night shift belongs to the Guards now. I'm off to bed.",
                    "Stars are out over {colony}. See you all bright and early tomorrow!",
                    "My muscles are sore from top to bottom—sleep is going to feel amazing.",
                    "Time to hang up my tools and call it a night.",
                    "Goodnight, {friend}! Don't stay up working too late!",
                    "Heading back to my Residence before it gets any darker out."
            )
    ),

    MORNING_COMMUTE(
            EventType.GENERAL,
            0xFFEE88,
            List.of(
                    "Fresh morning air in {colony}! Time to open up the {building}.",
                    "Coffee—well, warm tea—and an early start! Ready for today's shift as {job}.",
                    "The sun's barely up over {colony}, and there's already plenty to do at the {building}.",
                    "Slept like a rock last night! Ready to tackle whatever today brings.",
                    "Morning, {friend}! Off to another busy day at the {building}!",
                    "Nothing beats the quiet of {colony} right at sunrise.",
                    "Stretching the shoulders and heading to work—let's make today count!",
                    "Dew's still on the grass, and the {building} is waiting for me!",
                    "A brand new morning in {colony}! Let's see what's on the workboard today.",
                    "Up with the sun! A {job}'s work starts early."
            )
    ),

    HUNGRY(
            EventType.SATURATION_LOW,
            0xFFFF55,
            List.of(
                    "My stomach is growling louder than the {building}. Time to visit the Restaurant!",
                    "Hard work as a {job} really builds up an appetite!",
                    "Can't focus on an empty stomach—need to grab a warm meal soon.",
                    "I could eat a whole loaf of bread right about now...",
                    "Is that fresh food I smell coming from the Restaurant? Count me in!",
                    "Arms are feeling weak from hunger—time for a quick lunch break.",
                    "Been working at the {building} for hours without a bite. Off to get some food!",
                    "Hope the Cook still has something hot on the stove today.",
                    "A hungry {job} is a clumsy {job}—better go eat before I make a mistake.",
                    "Wonder what's on the menu at the Restaurant this {time}?",
                    "Just pausing my work at the {building} to refuel at the dining hall.",
                    "Even a simple baked potato would taste like a royal feast right now!"
            )
    ),

    DINING_SATISFIED(
            EventType.SATURATION_HIGH,
            0x55FF55,
            List.of(
                    "Nothing beats a warm, hearty meal after working at the {building}!",
                    "Compliments to the Cook! That meal really hit the spot.",
                    "Well fed and full of energy—ready to get back to my duties as {job}!",
                    "Now that was delicious! The food in {colony} keeps getting better.",
                    "A full belly makes the rest of the shift at the {building} fly right by.",
                    "Hot food and a quick sit-down—just what I needed this {time}.",
                    "Whoever baked the bread today deserves a raise!",
                    "Fully recharged! Back to the {building} with a smile on my face.",
                    "That stew warmed me right up. Time to get back to work!",
                    "Eating well is the secret to keeping {colony} running at top speed.",
                    "Great meal, great company at the table—now back to the {building}!",
                    "Couldn't eat another bite if I tried. Back to my craft!"
            )
    ),

    RAIN_AND_STORM(
            EventType.BAD_WEATHER,
            0x55AAFF,
            List.of(
                    "Working out in this rain is soaking my clothes right through!",
                    "Looks like those gray clouds over {colony} aren't clearing up anytime soon.",
                    "My boots are squelching in the mud with every step...",
                    "At least the Farmer's crops will love this rain, even if my cloak doesn't!",
                    "Water's dripping right down my collar—what a soggy {time}.",
                    "Can barely keep my grip on my tools with everything this wet!",
                    "Hope the roof on the {building} isn't leaking in this downpour.",
                    "Nothing dries out properly when it rains like this in {colony}.",
                    "A hot bowl of soup by the fire is going to feel amazing after this rain.",
                    "Careful on the paths, {friend}—the cobblestones are slick from the rain!",
                    "Puddles everywhere! Definitely wishing I'd brought a heavier coat today.",
                    "Rain or shine, the {building} still needs tending—though I'd prefer the shine!"
            )
    ),

    THUNDERSTORM(
            EventType.BAD_WEATHER,
            0x3388FF,
            List.of(
                    "Did you hear that thunder?! That lightning bolt struck way too close to {colony}!",
                    "The whole sky just lit up! Hope our rooftops are safe from lightning.",
                    "This gale is howling fiercely—watch out for falling branches!",
                    "Working outdoors in a thunderstorm is nerve-wracking... keep your head low!",
                    "That crack of thunder rattled the windows on the {building}!",
                    "Nasty storm rolling over {colony}! Even the monsters love weather this dark.",
                    "Rain's coming down sideways in this wind! What a tempest!",
                    "Every flash of lightning makes me jump—hope this storm blows over soon.",
                    "Batten down the hatches at the {building}! This storm means business.",
                    "Stay clear of tall trees and high towers until the lightning stops!"
            )
    ),

    MISSING_TOOLS_OR_MATERIALS(
            EventType.MISSING_EQUIPMENT,
            0xFFAA00,
            List.of(
                    "Waiting on fresh supplies at the {building} before I can continue my work.",
                    "I could really use the right tools here at the {building}.",
                    "Hopefully the Courier brings the requested materials to the {building} soon.",
                    "Can't do proper {job} work bare-handed—put in a request at the Warehouse!",
                    "All prepped and ready at the {building}, just missing the right equipment.",
                    "Checked the racks twice, and we're completely out of stock here at the {building}.",
                    "Hey {player}, if you have a spare moment, we're short on supplies at the {building}!",
                    "My old tool finally gave out... waiting for a replacement to arrive.",
                    "Logistics bottleneck at the {building}—standing by until the delivery gets here.",
                    "Even the best {job} in {colony} needs proper gear to get the job done!",
                    "Organizing the workbench while I wait for the Courier's next drop-off.",
                    "Looks like our supply chest at the {building} ran dry—request is in the system!"
            )
    ),

    HOME_COMFORT_GOOD(
            EventType.GOOD_HOUSING,
            0x55FF55,
            List.of(
                    "It feels wonderful having such a spacious, comfortable home to return to!",
                    "Our residences in {colony} have really improved—living here is a true joy.",
                    "Home sweet home! A cozy level {building_level} house makes all the hard work worthwhile.",
                    "Warm hearth, sturdy walls, and a dry roof—the Builders outdid themselves on our homes!",
                    "Always love walking past my Residence; it's the nicest neighborhood in {colony}.",
                    "Having a proper bedroom and good furniture makes resting so much better.",
                    "Look at the craftsmanship on our houses now! {colony} is turning into a proper city.",
                    "My home is so cozy I almost don't want to leave for the {building} in the morning!",
                    "Clean floors, warm lantern light, and plenty of space—couldn't ask for a better home.",
                    "Living in a high-tier Residence really lifts everyone's spirits around {colony}."
            )
    ),

    HOME_COMFORT_BAD(
            EventType.BAD_HOUSING,
            0xFFAA00,
            List.of(
                    "My Residence is getting a bit cramped... hopefully the Builder upgrades it soon.",
                    "A slightly bigger house would make resting after a shift at the {building} much nicer.",
                    "It's a humble roof over my head, though a house upgrade wouldn't hurt.",
                    "The floorboards creak and there's a draft by the door, but it keeps the rain off.",
                    "Sharing tight quarters isn't easy—dreaming of the day our Residence gets upgraded!",
                    "Could really use a little more storage space and a softer bed at home.",
                    "Our little cottage is showing its age compared to the newer buildings in {colony}.",
                    "Maybe once the Builders finish their current project, they can renovate our street!",
                    "A bit chilly inside my house at night, though I'm grateful to have a place in {colony}.",
                    "Hoping the Mayor puts in a work order to expand our Residence soon!"
            )
    ),

    GUARD_REASSURANCE(
            EventType.INTERACTION,
            0x55FFFF,
            List.of(
                    "Sector around the {building} is all clear. Carry on safely, everyone!",
                    "Keeping watch over this district of {colony}—no monsters getting past me.",
                    "Patrol check complete near the {building}. Everything looks secure!",
                    "Morning rounds are going smoothly—keep up the great work, {friend}!",
                    "Don't mind me, just checking the perimeter and blind spots around the {building}.",
                    "Eyes on the shadows, weapon at the ready. You're all safe to focus on your craft.",
                    "Quiet {time} on my patrol route so far—just the way we like it in {colony}.",
                    "Spreading out across the district so every workshop has a Guard nearby.",
                    "All quiet along the walls! Give a shout if you spot anything suspicious, {friend}.",
                    "Checking in on the {building}—perimeter's locked down tight.",
                    "Walking the beat through {colony}. Stay safe and productive out there!",
                    "You handle the crafting, {friend}, and I'll handle anything with teeth or a bow."
            )
    ),

    WORKER_THANKS_GUARD(
            EventType.INTERACTION,
            0x55FF55,
            List.of(
                    "Good to see {guard} patrolling nearby. Makes working at the {building} feel much safer!",
                    "Thanks for keeping watch over our street, {guard}!",
                    "With {guard} on patrol in this sector, I can focus completely on my work as {job}.",
                    "Always a relief hearing {guard}'s footsteps outside the {building}.",
                    "Stay safe out there on patrol, {guard}! {colony} counts on you.",
                    "Ever since the Guards spread their patrols out, the whole neighborhood feels safer.",
                    "Appreciate you checking in on the {building}, {guard}!",
                    "No creepers sneaking up on me while {guard} is watching this corner!",
                    "Having {guard} stationed near our workshops really puts my mind at ease.",
                    "Salute to {guard}! Our Guards work just as hard as the rest of us.",
                    "Sleep easier at night and work calmer by day thanks to {guard}.",
                    "Good luck on your rounds this {time}, {guard}!"
            )
    ),

    SOCIAL_CHATTER(
            EventType.INTERACTION,
            0xAAFFAA,
            List.of(
                    "Hey {friend}, how are things going over at your workshop this {time}?",
                    "Always good running into {friend} on the path! Let's catch up at the Tavern later.",
                    "{friend} and I were just talking about how fast {colony} has been expanding!",
                    "Keep up the great work, {friend}! Between the two of us, {colony} is in good hands.",
                    "Did you try the meal at the Restaurant today, {friend}? It was fantastic!",
                    "Good seeing you, {friend}! Don't work yourself too hard today.",
                    "Hey {friend}, have you seen how nice the new buildings in {colony} look?",
                    "Quick wave to {friend} while I'm heading back to the {building}!",
                    "Nothing makes a long shift better than friendly neighbors like {friend}.",
                    "Bet I finish my quota at the {building} before you finish yours, {friend}!",
                    "Save me a seat at the dining table later, {friend}!",
                    "Working alongside folks like {friend} is the best part of living in {colony}."
            )
    ),

    PLAYER_GREETING(
            EventType.GREETING,
            0x55FF55,
            List.of(
                    "Good {time}, {player}! Everything at the {building} is coming along nicely.",
                    "Hello there, {player}! Always glad to see you checking in on {colony}.",
                    "Greetings, {player}! Just tending to my duties as {job} at the {building}.",
                    "Oh, hey {player}! Passing through our corner of {colony}?",
                    "Welcome back, {player}! The {building} is running like clockwork today.",
                    "Fine {time}, isn't it, {player}? Let me know if you need anything from the {building}!",
                    "Good to see you out and about, {player}! {colony} is thriving under your leadership.",
                    "Hey {player}! Putting our level {building_level} {building} to good use today!",
                    "At your service, {player}! Proud to do my part as {job} here in {colony}.",
                    "Look who's here! Good {time}, {player}—hope your travels have been safe!",
                    "Everything's shipshape at the {building}, {player}!",
                    "Always inspiring when {player} stops by to see how the work is progressing!",
                    "Take care out there, {player}! We'll keep {colony} humming while you're busy.",
                    "Hello, {player}! Just another productive {time} here at the {building}."
            )
    ),

    PLAYER_FAREWELL(
            EventType.FAREWELL,
            0x88FFAA,
            List.of(
                    "Safe travels out there! We'll keep the colony running strong while you're away!",
                    "Take care of yourself! See you next time you stop by our district!",
                    "Until next time! I'll get right back to my tasks!",
                    "Watch your step out in the wilds—come back to us safe and sound!",
                    "Good luck on your journey! We've got everything covered here in town!",
                    "See you later! Have a wonderful rest of your day!",
                    "Farewell for now! Don't be a stranger!",
                    "Back to the craft for me—catch you later, friend!"
            )
    ),

    ACTIVE_JOB_WORK(
            EventType.SUCCESS,
            0xFFFFFF,
            List.of(
                    "Making steady progress at the {building} this {time}.",
                    "Another productive shift as {job} for {colony}!",
                    "Keeping the {building} running smoothly today.",
                    "Focused and in the rhythm—there's plenty of {job} work to get through!",
                    "Putting our level {building_level} {building} to full use this {time}.",
                    "Step by step, every task at the {building} helps {colony} prosper.",
                    "Quality craftsmanship takes patience, and today's work is looking great!",
                    "Checking off one task after another at the {building}.",
                    "Nothing more satisfying than seeing a hard day's work pay off for {colony}.",
                    "Keeping my workspace tidy and the production moving right along!"
            )
    ),

    LEISURE_AND_HAPPY(
            EventType.HAPPY,
            0x55FF55,
            List.of(
                    "Taking a short breather to enjoy the lively atmosphere around {colony}.",
                    "It's honestly inspiring to see how much {colony} has grown!",
                    "A fine {time} to catch my breath before the next task at the {building}.",
                    "Life in {colony} treats us well—good friends, solid work, and a real community.",
                    "Just stretching my legs and admiring the streets of {colony}.",
                    "You can really feel the pride everyone takes in keeping {colony} running.",
                    "Whistling a tune while I take a quick stroll—what a pleasant {time}!",
                    "Moments like this make all the busy shifts at the {building} worth it.",
                    "Looking around at our town... we've built something truly special here.",
                    "Enjoying a peaceful pause before jumping back into my {job} duties.",
                    "The air feels great this {time}—morale is high across {colony}!",
                    "Can't help but smile seeing everyone bustling around town."
            )
    ),

    UNHAPPY_MOOD(
            EventType.UNHAPPY,
            0xFFFF55,
            List.of(
                    "Been a rough few days lately... hope things look up for {colony} soon.",
                    "Feeling a little worn out today, but I'll keep doing my part at the {building}.",
                    "Could really use a bit of good news around {colony}.",
                    "Sigh... some shifts at the {building} just feel twice as long as usual.",
                    "My spirits are a bit low today—maybe a good meal and rest will help.",
                    "Things have been stressful around town lately. Hope quieter days are ahead.",
                    "Just trying to push through the fatigue and finish my work for the {time}.",
                    "Not my brightest day in {colony}, though I'm hanging in there.",
                    "A little peace and quiet is all I'm asking for right now.",
                    "Hoping tomorrow starts off on a better foot than today did."
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
     * Returns rich, profession-authentic English work templates for {@link #ACTIVE_JOB_WORK}
     * covering all MineColonies jobs.
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
                    "Brick by brick, another project for {colony} comes together.",
                    "Clearing the site and laying foundations—a Builder's work is never done!",
                    "Checking the blueprints again to make sure every beam and stair lines up.",
                    "Hauling timber and stone up the scaffolding—wait until {player} sees this finished!",
                    "Leveling the ground and setting the cornerstones. Strong foundations make strong buildings!",
                    "Trowel, plumb line, and hammer—turning raw materials into {colony}'s skyline.",
                    "This roofline was tricky, but the schematic is coming together beautifully.",
                    "Upgrading {colony} one block at a time! The difference is already night and day.",
                    "Making sure every wall is sturdy enough to stand for generations."
            );
        }
        if (key.contains("planter") || key.contains("plantation")) {
            return List.of(
                    "Tending the plantation rows—bamboo, sugar cane, and cocoa growing fast!",
                    "Harvesting fresh stalks at the {building} while leaving the roots intact to regrow.",
                    "Plenty of paper and sugar supplies heading to the Warehouse from our {building} today!",
                    "Careful around the cactus beds—those spines will poke right through your gloves!",
                    "Keeping the plantation channels watered and the harvest baskets full.",
                    "The tropical crops at the {building} are thriving this {time}!"
            );
        }
        if (key.contains("farmer")) {
            return List.of(
                    "The soil looks rich and fertile today—this harvest will fill the Warehouse nicely!",
                    "Tending the fields and replanting fresh seeds while the {time} light lasts.",
                    "A healthy multi-season crop rotation keeps the whole colony well fed.",
                    "Hoeing the rows and pulling weeds so every sprout gets enough sunlight.",
                    "Look at those golden stalks! {colony} won't be going hungry on my watch.",
                    "Checking soil moisture around the scarecrow—the fields are in prime condition.",
                    "From seed to bushel, honest fieldwork is the backbone of {colony}.",
                    "Gathering ripe crops into crates before the birds get to them first!",
                    "Fresh produce straight from the fields of the {building} to the Cook's kitchen!"
            );
        }
        if (key.contains("quarrier")) {
            return List.of(
                    "Cutting clean stone blocks from the quarry pit—our Builders go through stone fast!",
                    "Layer by layer, carving deep into the quarry for {colony}'s masonry.",
                    "Steady with the pickaxe—excavating another terrace down in the quarry.",
                    "Hauling freshly cut cobble and slate up from the quarry floor.",
                    "Nothing builds a fortress like solid quarry stone from our {building}!",
                    "Keeping the quarry ramps clear and the stone shipments flowing."
            );
        }
        if (key.contains("miner")) {
            return List.of(
                    "The rock strata down here are tough, but the ore veins are worth every swing!",
                    "Reinforcing the shaft timbers and digging deeper beneath {colony}.",
                    "Plenty of stone, coal, and iron heading up from the {building} today!",
                    "Placing fresh torches along the tunnel so no monsters spawn in the dark.",
                    "Listen to the ring of the pickaxe—there's a rich seam right behind this wall!",
                    "Checking the support beams at level {building_level} of the Mine before clearing the node.",
                    "Dust on my boots and ore in my pack—that's a good shift down in the {building}!",
                    "Careful around the deep caverns; always carrying cobble to seal off lava pockets.",
                    "Bringing the treasures of the earth up to the surface for our Blacksmiths and Smelters!"
            );
        }
        if (key.contains("lumberjack") || key.contains("forester")) {
            return List.of(
                    "Felling mature timber and replanting saplings so our grove stays lush.",
                    "Freshly cut logs stacked and ready for the Sawmill and Builders!",
                    "Nothing like the rhythm of a sharp axe in the {time} air.",
                    "Clearing out the high branches and making sure every stump gets a new sapling.",
                    "Oak, spruce, or birch—if {colony} needs wood, the {building} delivers!",
                    "Timber! Watch your step—another tall trunk coming down cleanly.",
                    "Keeping the forest sustainable so {colony} never runs short on lumber.",
                    "Sharpening my axe blade between trees; clean cuts make faster work!"
            );
        }
        if (key.contains("deliveryman") || key.contains("courier")) {
            return List.of(
                    "Special delivery! Keeping supplies flowing between the Warehouse and the huts.",
                    "So many requests on the clipboard today, but my routes are faster than ever!",
                    "Sorting crates and running batches—logistics is the lifeblood of {colony}.",
                    "Picking up finished goods from the {building} and rushing raw materials out!",
                    "No time to stand still—half the workshops in {colony} are waiting on this load!",
                    "Grouping deliveries along the same street saves so much legwork.",
                    "Checking the Warehouse racks—everything labeled, stacked, and ready to move!",
                    "Fast feet and a sturdy pack! Another round of deliveries completed for {colony}.",
                    "Out of the way, coming through with high-priority supplies!"
            );
        }
        if (key.contains("baker")) {
            return List.of(
                    "Kneading the dough and stoking the brick oven—fresh bread coming right up!",
                    "Nothing smells better than golden loaves rising at the {building}!",
                    "Dusting the counter with flour and pulling a warm batch from the oven.",
                    "Crispy crust on the outside, soft and warm on the inside—{colony}'s favorite!",
                    "Baking bread, rolls, and sweet pastries to keep everyone's spirits high.",
                    "Watch your fingers—these baking trays just came out of the fire!"
            );
        }
        if (key.contains("cook") || key.contains("chef")) {
            return List.of(
                    "The ovens are hot and fresh meals are simmering at the {building}!",
                    "Keeping the dining tables stocked so nobody in {colony} works on an empty stomach.",
                    "Smells delicious in the {building} today—just added a pinch of seasoning!",
                    "Chopping vegetables, roasting meat, and plating warm portions for the lunch rush.",
                    "A well-fed colonist works twice as fast—that's the rule of my kitchen!",
                    "Checking the pantry stock at the {building} to plan today's menu.",
                    "Stirring the pot and keeping the hearth fire steady this {time}.",
                    "Come on in while it's hot! Freshly cooked meals ready for {colony}!"
            );
        }
        if (key.contains("healer")) {
            return List.of(
                    "Checking on patients and organizing herbal remedies at the Hospital.",
                    "Always ready with clean bandages and tonics whenever a colonist needs care.",
                    "A healthy colony is a happy colony—keeping sickness at bay!",
                    "Crushing medicinal herbs in the mortar and restocking the treatment beds.",
                    "Making sure our Guards have salves ready after their patrols.",
                    "Cleanliness, rest, and the right cure—nobody stays sick for long at our {building}!",
                    "Reviewing my medical notes at the {building}—everyone's recovery is on track."
            );
        }
        if (key.contains("teacher") || key.contains("pupil") || key.contains("school")) {
            return List.of(
                    "Practicing lessons at the School—growing sharper and more skilled every day!",
                    "Studying hard today so tomorrow's workers make {colony} proud.",
                    "Reading, arithmetic, and colony history—knowledge starts right here at the {building}!",
                    "Taking notes at the {building}; you never stop learning in {colony}."
            );
        }
        if (key.contains("research") || key.contains("university")) {
            return List.of(
                    "Cross-referencing these ancient scrolls... we're close to a breakthrough for {colony}!",
                    "Every chapter studied at the University unlocks new techniques for our workers.",
                    "Fascinating findings in the library stacks this {time}!",
                    "Dipping the quill and documenting a new structural theory at the {building}.",
                    "Our research at the University is going to make the whole colony more efficient!",
                    "Carefully turning these brittle pages—there's centuries of wisdom in here.",
                    "Testing a new hypothesis at the {building}; science marches on in {colony}!"
            );
        }
        if (key.contains("enchanter")) {
            return List.of(
                    "Channeling arcane mana into fresh tomes at the {building}.",
                    "The enchantments are resonating strongly around the altar today!",
                    "Gathering experience and inscribing glowing runes for our workers.",
                    "Careful with the lapis and parchment—one mispronounced syllable ruins the spell!",
                    "Weaving protective and efficiency charms into books at our level {building_level} {building}.",
                    "The mystic energy in the {building} is humming this {time}!",
                    "Another ancient tome deciphered and bound with fresh magic for {colony}."
            );
        }
        if (key.contains("concrete")) {
            return List.of(
                    "Mixing and curing batches of concrete—rock-solid material for our Builders!",
                    "Powder in the water, vibrant blocks out—batch work at the {building} makes it fly by!",
                    "Scooping cured concrete from the basin and setting the next color batch.",
                    "Smooth, durable, and colorful—nothing modernizes {colony} like fresh concrete!",
                    "Keeping the water basin clear and the sand-and-gravel mix flowing."
            );
        }
        if (key.contains("blacksmith")) {
            return List.of(
                    "Strike while the iron is hot! Forging sturdy tools and armor at the {building}.",
                    "The ring of the anvil and the hiss of quenched steel—music to a Blacksmith's ears!",
                    "Hammering out dents and tempering blades so our Guards and workers have the best gear.",
                    "Pumping the bellows to get the forge glowing bright orange!",
                    "A well-balanced pickaxe or sword starts with pure ingots and a steady hammer.",
                    "Checking the edge on this newly forged batch at the {building}—razor sharp!"
            );
        }
        if (key.contains("smelter") || key.contains("stonesmelter")) {
            return List.of(
                    "Stoking the furnaces at the {building}—turning raw ore into gleaming ingots!",
                    "It's sweltering beside these blast furnaces, but {colony} needs refined metal!",
                    "Skimming the slag and pouring molten metal into ingot molds.",
                    "Keeping the coal fires roaring so the smelting batch finishes on time.",
                    "Freshly smelted stone, bricks, and ingots coming out of the {building} kilns!",
                    "Watching the furnace temperature closely—every ore needs the right heat."
            );
        }
        if (key.contains("carpenter") || key.contains("sawmill")) {
            return List.of(
                    "Sawdust in the air and the buzz of the blade—turning logs into precision planks!",
                    "Crafting stairs, doors, and timber frames at the {building} for our Builders.",
                    "Measuring the grain before cutting; good carpentry never wastes a single board!",
                    "Sanding down the edges and fitting the joints tightly at the {building}.",
                    "The Sawmill is running at full tilt today to keep up with {colony}'s work orders!"
            );
        }
        if (key.contains("stonemason")) {
            return List.of(
                    "Chiseling rough stone into clean bricks, pillars, and slabs at the {building}.",
                    "Steady hand with the mallet and chisel—good stonework lasts forever!",
                    "Carving decorative blocks and stairs to give {colony} a grand look.",
                    "Brushing off the stone dust and stacking finished masonry for the Courier."
            );
        }
        if (key.contains("cowboy")) {
            return List.of(
                    "Milking the herd and filling fresh bottles at the {building}!",
                    "The cattle are calm and well-fed today—plenty of milk and mushroom stew ready.",
                    "Tending the barn, brushing the cows, and keeping the troughs full of wheat.",
                    "Healthy livestock at the {building} means rich dairy and leather for {colony}!"
            );
        }
        if (key.contains("shepherd")) {
            return List.of(
                    "Shearing thick fleeces of wool at the {building}—the flock is looking great!",
                    "Counting the sheep and keeping their pasture clean and secure.",
                    "Bundles of soft wool ready for our Dyers, Builders, and tailors!",
                    "Snip, snip! A fresh trim for the sheep and warm wool for {colony}."
            );
        }
        if (key.contains("beekeeper")) {
            return List.of(
                    "Gentle puff of smoke to calm the bees before harvesting golden honeycombs!",
                    "Listen to that busy hum—our hives at the {building} are overflowing with honey!",
                    "Bottling sweet honey and collecting beeswax while the flowers are in bloom.",
                    "The bees are doing a wonderful job pollinating the flowers around the {building}."
            );
        }
        if (key.contains("herder") || key.contains("swine") || key.contains("chicken") || key.contains("rabbit") || key.contains("butcher")) {
            return List.of(
                    "The animals at the {building} are healthy, fed, and well cared for today.",
                    "Fresh eggs, meat, and stock from the pens—our livestock are thriving!",
                    "Scattering feed and cleaning out the pens this {time} at the {building}.",
                    "Making sure the gates are latched tight so none of the animals wander off!"
            );
        }
        if (key.contains("fisherman")) {
            return List.of(
                    "Casting my line into the water—the fish are definitely biting this {time}!",
                    "Mending the nets and sorting today's fresh catch at the {building}.",
                    "Patience and a steady rod! Hauling in another big fish for the Restaurant.",
                    "The water's calm and the bobber just dipped—got one on the hook!"
            );
        }
        if (key.contains("alchemist") || key.contains("nether")) {
            return List.of(
                    "Carefully measuring volatile reagents and brewing potions at the {building}.",
                    "Blaze powder, nether wart, and glass vials—alchemy requires absolute focus!",
                    "Braving the heat and hazards to bring rare Nether materials back to {colony}!",
                    "Checking the distillation apparatus at the {building}—this batch is potent."
            );
        }
        if (key.contains("florist") || key.contains("compost")) {
            return List.of(
                    "Turning organic scraps into rich compost and bonemeal for {colony}'s farms!",
                    "Cultivating vibrant blooms at the {building} to brighten up the whole town.",
                    "Nothing goes to waste—rich soil from the Composter keeps our fields green!"
            );
        }
        if (key.contains("glassblower") || key.contains("dyer") || key.contains("fletcher") || key.contains("mechanic") || key.contains("crusher") || key.contains("sifter")) {
            return List.of(
                    "Precision artisan work at the {building}—keeping {colony} supplied with specialty goods!",
                    "Checking the mechanisms and batches at the {building}; output is right on target.",
                    "Fine craftsmanship and steady hands make all the difference in this trade.",
                    "Another batch processed and ready for the Courier to pick up from the {building}!"
            );
        }
        if (key.contains("undertaker")) {
            return List.of(
                    "Tending the Graveyard with quiet care and watching over our colonists.",
                    "Keeping the memorial grounds peaceful, clean, and solemn at the {building}.",
                    "Polishing the headstones and making sure no undead disturb the peace."
            );
        }
        if (key.contains("ranger") || key.contains("archer")) {
            return List.of(
                    "Bowstring taut, quiver full—keeping a sharp eye on the approaches to {colony}.",
                    "Scanning the tree line from my vantage point. Nothing sneaks up on a Ranger!",
                    "Checking the wind and patrolling my assigned sector around the {building}.",
                    "Long-range overwatch active—our workers can go about their day in peace."
            );
        }
        if (key.contains("druid")) {
            return List.of(
                    "Mistletoe ready and nature's blessing upon {colony}—standing watch on patrol!",
                    "Keeping my allies bolstered and the perimeter around the {building} safe.",
                    "Harmonizing with the land while guarding the streets of {colony}."
            );
        }
        if (key.contains("guard") || key.contains("knight")) {
            return List.of(
                    "Eyes sharp, shield ready—keeping my assigned patrol sector secure.",
                    "Scanning the horizon along my patrol route. All quiet in {colony} so far.",
                    "Standing watch so the rest of {colony} can build and craft in peace.",
                    "Armor buckled and blade sharpened—making my rounds past the {building}.",
                    "Spreading out across town to make sure every neighborhood is covered.",
                    "Footsteps steady, ears open—no raiders or monsters getting into {colony} today!"
            );
        }
        return ACTIVE_JOB_WORK.defaultTemplates;
    }
}
