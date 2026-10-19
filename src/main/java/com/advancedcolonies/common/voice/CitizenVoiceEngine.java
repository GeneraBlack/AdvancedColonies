package com.advancedcolonies.common.voice;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.CitizenCommonConfig;
import com.advancedcolonies.network.CitizenVoicelinePayload;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.citizen.AbstractCivilianEntity;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context-reactive citizen voiceline and synchronized 3D audio routing engine:
 * <ul>
 *   <li>Evaluates a citizen's live surroundings (actual sky exposure during rain/storms, active raids,
 *       visible surface hostiles, health/retreat, sickness, mourning, hunger, home proximity, nearby
 *       patrolling Guards, nearby players, and current AI work state).</li>
 *   <li>Selects the exact matching {@code .ogg} voice clip for the citizen's gender, voice profile,
 *       role, and situation so the spoken audio and floating 3D speech bubble match 100% word-for-word.</li>
 *   <li>Broadcasts synchronized entity-bound 3D audio and speech bubbles to nearby players while
 *       strictly enforcing probability rolls, server cooldowns, and range limits.</li>
 * </ul>
 */
public final class CitizenVoiceEngine {

    /** Per-citizen last voiceline timestamp (game time ticks). */
    private static final Map<Long, Long> CITIZEN_LAST_SPOKE_TICK = new ConcurrentHashMap<>();

    /** Per-chunk-sector (16x16 area) last ambient voiceline timestamp to prevent crowd chatter spam. */
    private static final Map<Long, Long> AREA_LAST_SPOKE_TICK = new ConcurrentHashMap<>();

    /** Per-citizen last chosen clip/template index to avoid repeating the exact same line twice in a row. */
    private static final Map<Long, Integer> CITIZEN_LAST_TEMPLATE_IDX = new ConcurrentHashMap<>();

    /** Re-entrancy guard when our engine invokes {@code SoundUtils.playSoundAtCitizenWith}. */
    private static final ThreadLocal<Boolean> INTERNAL_SOUND_DISPATCH = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Exact spoken transcripts for every {@code <event>_01} and {@code <event>_02} clip across the 4 voice profiles
     * (index {@code profileIdx * 2} = {@code _01}, {@code profileIdx * 2 + 1} = {@code _02}).
     */
    private static final Map<String, List<String>> EVENT_CLIP_TEXTS = Map.ofEntries(
            Map.entry("danger", List.of(
                    "Raiders in the colony! Everyone get inside and lock the doors, now!",
                    "We're under attack! Stay alert—get behind the Guards immediately!",
                    "Hostiles spotted right outside! Sound the alarm, hurry!",
                    "Monsters are closing in on the perimeter! Guards, to the front!",
                    "To arms! Don't let those raiders breach our defenses!",
                    "Watch out! There's fighting breaking out right near the workshops!",
                    "Drop your tools and run for cover! The colony is under attack!",
                    "Hold the line! Keep those hostiles away from the town center!"
            )),
            Map.entry("gotobed", List.of(
                    "Ugh, what a long shift... time to finally head home and get some sleep.",
                    "The sun's down and my feet are aching... heading off to bed.",
                    "Barely keeping my eyes open... my bed is definitely calling my name.",
                    "Exhausted from head to toe... goodnight, everyone.",
                    "Locking up the workshop for the night... time for some well-earned rest.",
                    "The night watch belongs to the Guards now... I'm finally off to sleep.",
                    "Stars are out and I'm completely worn out... see you all tomorrow.",
                    "Time to hang up my tools and call it a night..."
            )),
            Map.entry("badweather", List.of(
                    "Brrr! Working out in this freezing rain is soaking my clothes right through!",
                    "Ugh, looks like these storm clouds aren't letting up anytime soon!",
                    "My boots are squelching in the cold mud with every single step...",
                    "At least the Farmer's crops will love this rain, even if I'm freezing out here!",
                    "Did you hear that thunder?! Hope our rooftops hold up against this storm!",
                    "Cold water's dripping right down my collar—what a miserable downpour!",
                    "Careful on the paths, everyone! The cobblestones are super slick from the rain!",
                    "Shivering out here... a hot bowl of soup by the fire can't come soon enough!"
            )),
            Map.entry("lowsaturation", List.of(
                    "Ugh, my stomach is growling so loud... I really need a hot meal at the Restaurant.",
                    "Hours of hard labor really drains you... I'm starving.",
                    "Can't focus on an empty stomach anymore... heading off to find some food.",
                    "I'm so hungry I could eat a whole loaf of bread right about now...",
                    "My arms are feeling shaky from hunger... time to grab a bite to eat.",
                    "Been working non-stop without a break... really need to refuel soon.",
                    "Hope the Cook still has something warm simmering on the stove...",
                    "A starving worker is a clumsy worker... better go eat before I drop."
            )),
            Map.entry("highsaturation", List.of(
                    "Ahh, nothing beats a warm, hearty meal after a long shift!",
                    "Compliments to the Cook! That hot meal really hit the spot!",
                    "Well fed and full of energy again—ready to get right back to work!",
                    "Now that was delicious! The food in our colony keeps getting better and better!",
                    "A full belly and a warm drink—makes the whole workday fly right by!",
                    "Whoever baked the bread today deserves a medal! I feel great!",
                    "That rich stew warmed me right up! Back to my duties with a smile!",
                    "Fully recharged and feeling fantastic! Ready for the next task!"
            )),
            Map.entry("badhousing", List.of(
                    "Sigh... my residence is getting so cramped. Hopefully the Builder upgrades it soon.",
                    "A slightly bigger house would make resting after a hard shift much nicer...",
                    "It's a humble roof over my head, though a proper upgrade wouldn't hurt.",
                    "The floorboards creak and there's a cold draft by the door... wish it were cozier.",
                    "Could really use a little more room and a softer bed at home...",
                    "Our little cottage is really showing its age compared to the rest of town.",
                    "Maybe once the Builders finish their current project, they can fix up our street...",
                    "Hoping the Mayor puts in a work order to expand our cramped house soon."
            )),
            Map.entry("goodhousing", List.of(
                    "It feels wonderful having such a spacious, comfortable home to return to!",
                    "Our residences have improved so much—living in this colony is a true joy!",
                    "Home sweet home! A cozy, warm house makes all the hard work worthwhile!",
                    "Warm hearth, sturdy walls, and a dry roof—the Builders truly outdid themselves!",
                    "Having a proper bedroom and fine furniture makes resting feel amazing!",
                    "Just look at the craftsmanship on our houses now! The colony looks incredible!",
                    "My home is so cozy and inviting, I almost don't want to leave in the morning!",
                    "Clean floors, warm lantern light, and plenty of space—couldn't ask for a better home!"
            )),
            Map.entry("greeting", List.of(
                    "Hello there! Everything at the workshop is coming along wonderfully today!",
                    "Hey! Always so glad to see you checking in on our colony!",
                    "Good day to you! Just tending to my duties and keeping things running smoothly!",
                    "Welcome back! Let me know if you need anything at all from my hut!",
                    "Great to see you out and about! The colony is thriving under your leadership!",
                    "Hello, my friend! Hope your travels outside the colony have been safe!",
                    "At your service! Proud to do my part for the town today!",
                    "What a fine day, isn't it? Everything's shipshape over here!"
            )),
            Map.entry("farewell", List.of(
                    "Safe travels out there! We'll keep the colony running strong while you're away!",
                    "Take care of yourself! See you next time you stop by our district!",
                    "Until next time! I'll get right back to my tasks!",
                    "Watch your step out in the wilds—come back to us safe and sound!",
                    "Good luck on your journey! We've got everything covered here in town!",
                    "See you later! Have a wonderful rest of your day!",
                    "Farewell for now! Don't be a stranger!",
                    "Back to the craft for me—catch you later, friend!"
            )),
            Map.entry("missingequipment", List.of(
                    "Tsk, stuck waiting on fresh supplies before I can continue my work here!",
                    "I'm completely stalled without the right tools at my workbench right now!",
                    "Come on, where is that Courier? We urgently need those materials from the Warehouse!",
                    "Can't do proper work bare-handed! I've put in an urgent request for new gear!",
                    "Everything's prepped, but I'm missing the equipment to actually finish the job!",
                    "Checked the storage racks twice, and we're totally out of stock! How frustrating!",
                    "My old tool finally snapped in half... standing by until a replacement arrives!",
                    "Hands are tied until the next delivery gets here—we're out of materials!"
            )),
            Map.entry("happy", List.of(
                    "Haha, what a wonderful day! Just taking a moment to enjoy the lively atmosphere!",
                    "It's honestly so inspiring to see how much our town has grown and prospered!",
                    "Life in this colony treats us so well—great friends and a real community!",
                    "Just stretching my legs with a big smile—our town looks better every single day!",
                    "You can really feel the pride and joy everyone takes in our colony!",
                    "Moments like this make all the busy shifts at the workshop completely worth it!",
                    "Looking around at our bustling streets... we've built something truly special here!",
                    "Morale is sky-high today! Can't help but smile seeing everyone thriving!"
            )),
            Map.entry("unhappy", List.of(
                    "Sigh... it's been a really rough few days lately... hope things look up soon.",
                    "Feeling so worn down and heavy-hearted today... but I'll try to keep going.",
                    "Could really use a little bit of good news around town for a change...",
                    "Everything feels twice as hard today... my spirits are pretty low.",
                    "Still carrying a heavy heart after what we've been through... rest in peace, old friend.",
                    "Things have been so stressful lately... just praying for quieter, happier days ahead.",
                    "Barely have the heart to focus today... the colony feels so somber.",
                    "Hoping tomorrow brings a little warmth back, because today has been tough..."
            )),
            Map.entry("sick", List.of(
                    "Ugh... feeling so feverish all morning... I really need to see the Healer at the Hospital.",
                    "My head is spinning and I'm burning up... hope the Hospital has the medicine I need.",
                    "Can't stop coughing today... my chest hurts, I shouldn't push myself too hard.",
                    "Shivering with this awful chill... off to the Hospital before I collapse.",
                    "My throat feels like sandpaper and my knees are weak... Healer, please help...",
                    "Every single step feels like lead today... definitely caught a nasty fever.",
                    "Better get to a Hospital bed quickly before I pass this sickness to anyone else...",
                    "Not feeling like myself at all... dizzy, weak, and in need of a cure."
            )),
            Map.entry("interaction", List.of(
                    "Hey there, neighbor! Always great seeing a friendly face—let's catch up at the Tavern later!",
                    "Good to see the Guards patrolling our street! Makes working here feel so much safer!",
                    "Sector is all clear, friends! You can all focus on your craft in complete peace!",
                    "How's your shift going today? Save me a seat at the dining hall later!",
                    "Thanks for keeping watch over us, Guard! The whole neighborhood appreciates you!",
                    "Working alongside good friends like you is the best part of living in this colony!",
                    "Patrol check complete! Not a single monster getting near our workshops today!",
                    "Appreciate the help, neighbor! Together we keep this town running strong!"
            )),
            Map.entry("success", List.of(
                    "Yes! Another task finished cleanly! Look at that craftsmanship!",
                    "Haha, nailed it! Today's work is turning out better than ever!",
                    "Checking off one job after another—what an awesome, productive shift!",
                    "That's how a master artisan does it! Another batch completed for the colony!",
                    "Nothing more satisfying than seeing a hard day's work pay off this well!",
                    "Right on target! Output at the workshop is booming today!",
                    "That came together beautifully! Bring on the next work order!",
                    "Boom! Another milestone finished for our growing town!"
            )),
            Map.entry("general", List.of(
                    "Fresh air and a busy workshop—ready to make today count for the colony!",
                    "Keeping a steady rhythm and doing my part to help our town flourish!",
                    "Let's see what's next on the workboard—plenty of good work ahead!",
                    "Focused and in the zone! Everything at the hut is running smoothly!",
                    "Step by step, keeping the colony's production humming right along!",
                    "Always something exciting being built or crafted around town!",
                    "Checking the supplies and diving right into the next task!",
                    "Proud to ply my trade here—let's keep the momentum going!"
            )),
            Map.entry("noise", List.of(
                    "Hmm, let's see how this lines up...",
                    "Right then, steady does it...",
                    "Almost got it... just a little further.",
                    "Let's double-check that measurement real quick.",
                    "One careful step at a time...",
                    "Aha, looking good so far!",
                    "Alright, what's next on the workbench?",
                    "There we go—fits right into place!"
            ))
    );

    /**
     * Exact spoken transcripts for every {@code job_<family>_01} clip across the 4 voice profiles
     * (index {@code profileIdx} = {@code 0..3}).
     */
    private static final Map<String, List<String>> JOB_FAMILY_CLIP_TEXTS = Map.ofEntries(
            Map.entry("builder", List.of(
                    "Measuring twice and placing once—this new structure is really taking shape!",
                    "Brick by brick and beam by beam, another grand project for the colony comes together!",
                    "Checking the blueprints again—wait until the Mayor sees this building finished!",
                    "Laying solid foundations and raising the walls! A Builder's pride stands for generations!"
            )),
            Map.entry("farmer", List.of(
                    "The soil looks rich and fertile today—this harvest is going to fill the Warehouse to the brim!",
                    "Tending the fields and planting fresh seeds while the sun shines bright!",
                    "Look at those golden crops! Nobody in our colony is going hungry on my watch!",
                    "Fresh produce straight from our fields to the kitchen—what a bountiful season!"
            )),
            Map.entry("miner", List.of(
                    "The rock strata down here are tough, but striking a rich ore vein makes every swing worth it!",
                    "Reinforcing the shaft timbers and digging deeper—plenty of iron and coal coming up today!",
                    "Placing fresh torches along the tunnel and hauling up treasure from the deep!",
                    "Listen to the ring of my pickaxe—there's a rich seam of ore right behind this rock!"
            )),
            Map.entry("lumberjack", List.of(
                    "Timber! Watch your step—another tall trunk coming down cleanly for the Sawmill!",
                    "Felling mature timber and replanting saplings so our forest stays green and lush!",
                    "Freshly cut logs stacked high and ready for our Builders and Carpenters!",
                    "Nothing beats the crisp rhythm of a sharp axe out in the woods!"
            )),
            Map.entry("deliveryman", List.of(
                    "Special delivery coming through! Keeping supplies flowing fast between the Warehouse and huts!",
                    "My pack is full and my boots are flying—half the town is counting on this delivery!",
                    "Sorting crates and running batches across town! Logistics is the heartbeat of the colony!",
                    "Fast feet and optimized routes! Another round of high-priority deliveries done!"
            )),
            Map.entry("cook", List.of(
                    "The ovens are blazing hot and delicious meals are simmering in the kitchen!",
                    "Kneading fresh dough and roasting meat—come on in while the food is piping hot!",
                    "Keeping the dining tables stocked so every worker in town leaves with a full belly!",
                    "Smells incredible in the kitchen today—just added the secret seasoning!"
            )),
            Map.entry("healer", List.of(
                    "Checking on my patients and preparing fresh herbal remedies here at the Hospital!",
                    "Clean bandages, soothing tonics, and a caring hand—nobody stays sick for long on my watch!",
                    "Crushing medicinal herbs in the mortar so we're ready for any illness or injury!",
                    "A healthy colony is a happy colony—everyone's recovery is right on track!"
            )),
            Map.entry("researcher", List.of(
                    "Eureka! Cross-referencing these ancient scrolls—we're on the verge of a major breakthrough!",
                    "Every chapter studied at the University unlocks powerful new knowledge for our people!",
                    "Dipping my quill and documenting a brand-new discovery for the colony!",
                    "Knowledge is power! Our research today will make the entire town stronger tomorrow!"
            )),
            Map.entry("enchanter", List.of(
                    "Channeling pure arcane mana into fresh tomes—feel how strongly the altar resonates!",
                    "Inscribing glowing runes of protection and efficiency for our colony's workers!",
                    "Careful with the lapis and ancient syllables—binding powerful magic into this book!",
                    "The mystic energy in the tower is humming today—another enchanted tome complete!"
            )),
            Map.entry("blacksmith", List.of(
                    "Strike while the iron is glowing red-hot! Forging the finest tools and armor in the land!",
                    "The ring of the anvil and the hiss of quenched steel—that's music to a Blacksmith's ears!",
                    "Stoking the roaring blast furnaces to turn raw ore into gleaming metal ingots!",
                    "Tempering blades and hammering plate armor so our Guards are unstoppable!"
            )),
            Map.entry("carpenter", List.of(
                    "Sawdust in the air and the buzz of the blade—turning rough logs into precision planks!",
                    "Chiseling fine stone bricks and cutting timber frames for our colony's grand projects!",
                    "Mixing and curing rock-solid batches of concrete and masonry for the Builders!",
                    "Steady hands and fine craftsmanship—every joint and block fits together like clockwork!"
            )),
            Map.entry("herder", List.of(
                    "The animals in the pens are healthy, calm, and thriving under our care today!",
                    "Fresh milk, thick wool, golden honeycombs, and eggs—our pastures are overflowing!",
                    "Gentle puff of smoke for the beehives and fresh wheat in the troughs—livestock work is a joy!",
                    "Reeling in fresh fish and tending the herds to keep our colony's larders full!"
            )),
            Map.entry("guard", List.of(
                    "Eyes sharp, shield high, blade ready—keeping my assigned patrol sector locked down!",
                    "Scanning the horizon along my patrol route! No monsters or raiders getting past me!",
                    "Standing vigilant watch so every citizen in our colony can work and live in peace!",
                    "Spreading our patrol coverage across every street—this district is safe under my watch!"
            ))
    );

    /** Maps MineColonies role IDs to their voice job family. */
    private static final Map<String, String> ROLE_TO_JOB_FAMILY = Map.ofEntries(
            Map.entry("archertraining", "guard"),
            Map.entry("baker", "cook"),
            Map.entry("beekeeper", "herder"),
            Map.entry("blacksmith", "blacksmith"),
            Map.entry("builder", "builder"),
            Map.entry("cavalry", "guard"),
            Map.entry("chef", "cook"),
            Map.entry("chickenherder", "herder"),
            Map.entry("combattraining", "guard"),
            Map.entry("composter", "farmer"),
            Map.entry("concretemixer", "carpenter"),
            Map.entry("cook", "cook"),
            Map.entry("cookassistant", "cook"),
            Map.entry("cowboy", "herder"),
            Map.entry("crusher", "carpenter"),
            Map.entry("deliveryman", "deliveryman"),
            Map.entry("druid", "guard"),
            Map.entry("dyer", "carpenter"),
            Map.entry("enchanter", "enchanter"),
            Map.entry("farmer", "farmer"),
            Map.entry("fisherman", "herder"),
            Map.entry("fletcher", "carpenter"),
            Map.entry("florist", "farmer"),
            Map.entry("glassblower", "blacksmith"),
            Map.entry("healer", "healer"),
            Map.entry("knight", "guard"),
            Map.entry("lumberjack", "lumberjack"),
            Map.entry("mechanic", "carpenter"),
            Map.entry("miner", "miner"),
            Map.entry("netherworker", "miner"),
            Map.entry("planter", "farmer"),
            Map.entry("pupil", "researcher"),
            Map.entry("quarrier", "miner"),
            Map.entry("rabbitherder", "herder"),
            Map.entry("ranger", "guard"),
            Map.entry("researcher", "researcher"),
            Map.entry("sawmill", "carpenter"),
            Map.entry("shepherd", "herder"),
            Map.entry("sifter", "carpenter"),
            Map.entry("smelter", "blacksmith"),
            Map.entry("stablemaster", "herder"),
            Map.entry("stonemason", "carpenter"),
            Map.entry("stonesmeltery", "blacksmith"),
            Map.entry("student", "researcher"),
            Map.entry("swineherder", "herder"),
            Map.entry("teacher", "researcher")
    );

    /** Restricts which voice profile indices (0..3) can use the family's {@code job_<family>_01} clip for a specific role. */
    private static final Map<String, Set<Integer>> ROLE_ALLOWED_JOB_PROFILES = Map.ofEntries(
            Map.entry("composter", Set.of(0)),
            Map.entry("florist", Set.of(0, 1)),
            Map.entry("netherworker", Set.of(2)),
            Map.entry("pupil", Set.of(2, 3)),
            Map.entry("student", Set.of(2, 3)),
            Map.entry("teacher", Set.of(2, 3)),
            Map.entry("smelter", Set.of(2)),
            Map.entry("stonesmeltery", Set.of(2)),
            Map.entry("glassblower", Set.of(2)),
            Map.entry("sawmill", Set.of(0, 1, 3)),
            Map.entry("stonemason", Set.of(1, 2, 3)),
            Map.entry("concretemixer", Set.of(2, 3)),
            Map.entry("fletcher", Set.of(0, 3)),
            Map.entry("mechanic", Set.of(3)),
            Map.entry("dyer", Set.of(3)),
            Map.entry("crusher", Set.of(3)),
            Map.entry("sifter", Set.of(3)),
            Map.entry("beekeeper", Set.of(1, 2)),
            Map.entry("cowboy", Set.of(0, 1, 2)),
            Map.entry("shepherd", Set.of(0, 1, 2)),
            Map.entry("chickenherder", Set.of(0, 1, 2)),
            Map.entry("swineherder", Set.of(0, 2)),
            Map.entry("rabbitherder", Set.of(0, 2)),
            Map.entry("stablemaster", Set.of(0, 2)),
            Map.entry("fisherman", Set.of(3))
    );

    private CitizenVoiceEngine() {
    }

    /**
     * Returns {@code true} if the current thread is inside an internal sound dispatch initiated by this engine.
     */
    public static boolean isInternalSoundDispatch() {
        return Boolean.TRUE.equals(INTERNAL_SOUND_DISPATCH.get());
    }

    /**
     * Handles {@code SoundUtils.playRandomSound(Level, BlockPos, ICitizenData)} when context-reactive
     * voicelines or smart sound routing is enabled.
     *
     * @param world   the level
     * @param pos     the citizen block position
     * @param citizen the citizen data
     * @return {@code true} if handled by the context-reactive engine (cancelling vanilla random roll)
     */
    public static boolean handlePlayRandomSound(
            @Nullable final Level world,
            @Nullable final BlockPos pos,
            @Nullable final ICitizenData citizen) {

        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        if (!cfg.enableContextReactiveVoicelines.get() || world == null || pos == null || citizen == null) {
            return false;
        }

        if (!(world instanceof ServerLevel serverLevel) || citizen.getColony() == null) {
            return false;
        }

        // EntityCitizen.onLivingSoundUpdate() calls playRandomSound every 20 ticks (1s).
        // Roll a natural ambient probability before checking cooldowns so citizens don't speak
        // robotically on the exact tick their cooldown expires.
        if (serverLevel.random.nextFloat() >= 0.085F) {
            return true;
        }

        final int maxRange = Math.max(8, Math.min(64, cfg.voicelineMaxRangeBlocks.get()));
        final double maxRangeSqr = (double) maxRange * maxRange;

        Player nearestPlayer = null;
        double nearestPlayerDistSqr = Double.MAX_VALUE;
        for (final Player subscriber : citizen.getColony().getPackageManager().getCloseSubscribers()) {
            final double distSqr = subscriber.blockPosition().distSqr(pos);
            if (distSqr <= maxRangeSqr && distSqr < nearestPlayerDistSqr) {
                nearestPlayerDistSqr = distSqr;
                nearestPlayer = subscriber;
            }
        }

        if (nearestPlayer == null) {
            return true;
        }

        final long nowTick = serverLevel.getGameTime();
        if (!checkAndRecordCooldowns(citizen, pos, nowTick, false, false)) {
            return true;
        }

        final ResolvedVoiceSituation situation = evaluateSituation(
                serverLevel, pos, citizen, nearestPlayer, nearestPlayerDistSqr, null);
        dispatchAudioAndSpeechBubble(serverLevel, pos, citizen, situation, null);
        return true;
    }

    /**
     * Intercepts direct calls to {@code SoundUtils.playSoundAtCitizenWith} so explicit interactions
     * (player right-clicks, greetings, farewells, sickness, danger) respect MineColonies' probability
     * roll ({@code chance}) and dispatch 100% synchronized 3D audio + floating speech bubbles.
     *
     * @param world     the level
     * @param pos       the citizen position
     * @param eventType the requested MineColonies sound event type
     * @param civilian  the civilian/citizen data
     * @param chance    the percentage chance (0..100) passed to {@code playSoundAtCitizenWith}
     * @param volume    the volume passed to {@code playSoundAtCitizenWith}
     * @return {@code true} if handled by this engine (cancelling vanilla sound dispatch)
     */
    public static boolean handleDirectSoundAtCitizen(
            @Nullable final Level world,
            @Nullable final BlockPos pos,
            @Nullable final EventType eventType,
            @Nullable final ICivilianData civilian,
            final double chance,
            final double volume) {

        if (isInternalSoundDispatch()
                || !(world instanceof ServerLevel serverLevel)
                || pos == null
                || eventType == null
                || !(civilian instanceof ICitizenData citizen)) {
            return false;
        }

        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        if (!cfg.enableContextReactiveVoicelines.get()) {
            return false;
        }

        // Respect MineColonies' probability roll (e.g. 0.2% or 2.0% on background AI state loops,
        // 100.0% on explicit player right-click interaction).
        if (chance <= 0.0D || (chance < 100.0D && serverLevel.random.nextDouble() * 100.0D >= chance)) {
            return true;
        }

        final int maxRange = Math.max(8, Math.min(64, cfg.voicelineMaxRangeBlocks.get()));
        final Player nearestPlayer = serverLevel.getNearestPlayer(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, maxRange, false);
        if (nearestPlayer == null) {
            return true;
        }

        final boolean isPlayerInteraction = chance >= 99.0D && eventType == EventType.INTERACTION;
        final long nowTick = serverLevel.getGameTime();
        if (!checkAndRecordCooldowns(citizen, pos, nowTick, true, isPlayerInteraction)) {
            return true;
        }

        final double distSqr = nearestPlayer.blockPosition().distSqr(pos);
        final ResolvedVoiceSituation situation = evaluateSituation(
                serverLevel, pos, citizen, nearestPlayer, distSqr, eventType);
        dispatchAudioAndSpeechBubble(serverLevel, pos, citizen, situation, eventType);
        return true;
    }

    /**
     * Enforces per-citizen and per-area server cooldown limits.
     */
    private static boolean checkAndRecordCooldowns(
            @NotNull final ICitizenData citizen,
            @NotNull final BlockPos pos,
            final long nowTick,
            final boolean isDirectEvent,
            final boolean isPlayerInteraction) {

        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        final int colonyId = citizen.getColony() != null ? citizen.getColony().getID() : 0;
        final long citizenKey = (((long) colonyId) << 32) | (citizen.getId() & 0xFFFFFFFFL);

        final int minCitizenSeconds = Math.max(5, Math.min(600, cfg.voicelineMinCitizenCooldownSeconds.get()));
        final long minCitizenTicks;
        if (isPlayerInteraction) {
            minCitizenTicks = 40L;
        } else if (isDirectEvent) {
            minCitizenTicks = Math.max(60L, (minCitizenSeconds * 20L) / 3L);
        } else {
            minCitizenTicks = minCitizenSeconds * 20L;
        }

        final Long lastCitizenTick = CITIZEN_LAST_SPOKE_TICK.get(citizenKey);
        if (lastCitizenTick != null && (nowTick - lastCitizenTick) < minCitizenTicks) {
            return false;
        }

        final int minAreaTicks = Math.max(0, Math.min(1200, cfg.voicelineMinAreaCooldownTicks.get()));
        final long areaKey = (((long) (pos.getX() >> 4)) << 32) ^ ((pos.getZ() >> 4) & 0xFFFFFFFFL);
        if (!isDirectEvent && minAreaTicks > 0) {
            final Long lastAreaTick = AREA_LAST_SPOKE_TICK.get(areaKey);
            if (lastAreaTick != null && (nowTick - lastAreaTick) < minAreaTicks) {
                return false;
            }
        }

        CITIZEN_LAST_SPOKE_TICK.put(citizenKey, nowTick);
        AREA_LAST_SPOKE_TICK.put(areaKey, nowTick);
        return true;
    }

    /**
     * Evaluates the citizen's live environment, AI state, health, weather, and nearby entities
     * to determine the most coherent {@link CitizenVoiceContext}.
     */
    @NotNull
    private static ResolvedVoiceSituation evaluateSituation(
            @NotNull final ServerLevel world,
            @NotNull final BlockPos pos,
            @NotNull final ICitizenData citizen,
            @NotNull final Player nearestPlayer,
            final double nearestPlayerDistSqr,
            @Nullable final EventType explicitEventHint) {

        final IColony colony = citizen.getColony();
        final AbstractEntityCitizen entity = citizen.getEntity().orElse(null);
        final IJob<?> job = citizen.getJob();
        final boolean isGuard = job instanceof AbstractJobGuard<?>;

        final String citizenState = (entity instanceof EntityCitizen ec && ec.getCitizenAI() != null && ec.getCitizenAI().getState() != null)
                ? ec.getCitizenAI().getState().toString()
                : "";
        final String workerState = (job != null && job.getWorkerAI() instanceof AbstractEntityAIBasic<?, ?> ai && ai.getState() != null)
                ? ai.getState().toString()
                : "";

        // 1. Check active combat, guard retreat, raid, or visible nearby surface hostiles
        if (isGuard && workerState.contains("FLEE")) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.GUARD_RETREAT, nearestPlayer, null, null);
        }

        final boolean raidActive = colony != null && colony.getRaiderManager() != null && colony.getRaiderManager().isRaided();
        final boolean hostilesVisibleNearby = entity != null && !world.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(pos).inflate(14.0D, 3.5D, 14.0D),
                e -> e instanceof Enemy && e.isAlive() && entity.hasLineOfSight(e)).isEmpty();

        if (raidActive
                || hostilesVisibleNearby
                || workerState.contains("ATTACK")
                || citizenState.contains("FLEE")
                || explicitEventHint == EventType.DANGER) {
            final CitizenVoiceContext combatContext = isGuard
                    ? CitizenVoiceContext.GUARD_COMBAT
                    : CitizenVoiceContext.RAID_COMBAT;
            return new ResolvedVoiceSituation(combatContext, nearestPlayer, null, null);
        }

        // 2. Check Sickness / Hospital visit
        final boolean isSick = (citizen.getCitizenDiseaseHandler() != null && citizen.getCitizenDiseaseHandler().isSick())
                || citizenState.contains("SICK")
                || workerState.contains("SICK");
        if (isSick || explicitEventHint == EventType.SICKNESS) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.SICK_OR_INJURED, nearestPlayer, null, null);
        }

        // 3. Check Mourning
        if (citizenState.contains("MOURN")
                || (citizen.getCitizenMournHandler() != null && citizen.getCitizenMournHandler().isMourning())) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.MOURNING, nearestPlayer, null, null);
        }

        // 4. Check Sleeping / Bedtime
        if (citizen.isAsleep() || citizenState.contains("SLEEP") || explicitEventHint == EventType.OFF_TO_BED) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.SLEEPING_OR_BEDTIME, nearestPlayer, null, null);
        }

        // 5. Explicit Farewell / Greeting hints
        if (explicitEventHint == EventType.FAREWELL) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.PLAYER_FAREWELL, nearestPlayer, null, null);
        }
        if (explicitEventHint == EventType.GREETING) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.PLAYER_GREETING, nearestPlayer, null, null);
        }

        // 6. Check Eating / Hunger
        if (explicitEventHint == EventType.SATURATION_HIGH
                || (citizenState.contains("EAT") && citizen.getSaturation() >= 7.0D)) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.DINING_SATISFIED, nearestPlayer, null, null);
        }
        if (citizen.getSaturation() < 3.0D || citizenState.contains("EAT") || explicitEventHint == EventType.SATURATION_LOW) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.HUNGRY, nearestPlayer, null, null);
        }

        // 7. Explicit Missing Equipment / Waiting for Materials (excluding normal idle-at-job states)
        final boolean waitingForMaterials = workerState.contains("NEEDS")
                || workerState.contains("GATHERING_REQUIRED_MATERIALS")
                || explicitEventHint == EventType.MISSING_EQUIPMENT;
        if (waitingForMaterials && job != null && !isGuard) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.MISSING_TOOLS_OR_MATERIALS, nearestPlayer, null, null);
        }

        // 8. Explicit Player Right-Click Interaction (EventType.INTERACTION)
        if (explicitEventHint == EventType.INTERACTION) {
            if (job != null && !citizen.isChild() && world.random.nextFloat() < 0.40F) {
                return new ResolvedVoiceSituation(CitizenVoiceContext.ACTIVE_JOB_WORK, nearestPlayer, null, null);
            }
            return new ResolvedVoiceSituation(CitizenVoiceContext.PLAYER_GREETING, nearestPlayer, null, null);
        }

        // 9. Explicit Housing / Mood / Work hints from MineColonies AI
        final IBuilding home = citizen.getHomeBuilding();
        if (explicitEventHint == EventType.GOOD_HOUSING && home != null && home.getBuildingLevelEquivalent() >= 3) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.HOME_COMFORT_GOOD, nearestPlayer, null, null);
        }
        if (explicitEventHint == EventType.BAD_HOUSING && home != null && home.getBuildingLevelEquivalent() <= 2) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.HOME_COMFORT_BAD, nearestPlayer, null, null);
        }
        if (explicitEventHint == EventType.HAPPY) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.LEISURE_AND_HAPPY, nearestPlayer, null, null);
        }
        if (explicitEventHint == EventType.UNHAPPY) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.UNHAPPY_MOOD, nearestPlayer, null, null);
        }
        if ((explicitEventHint == EventType.SUCCESS || explicitEventHint == EventType.NOISE) && job != null) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.ACTIVE_JOB_WORK, nearestPlayer, null, null);
        }

        // 10. Check Outdoor Thunderstorm or Rain Exposure (only when actually outdoors under open sky!)
        final boolean exposedToRain = world.isRaining() && world.canSeeSky(pos.above());
        if (exposedToRain && (explicitEventHint == EventType.BAD_WEATHER || world.random.nextFloat() < 0.45F)) {
            final CitizenVoiceContext weatherContext = world.isThundering()
                    ? CitizenVoiceContext.THUNDERSTORM
                    : CitizenVoiceContext.RAIN_AND_STORM;
            return new ResolvedVoiceSituation(weatherContext, nearestPlayer, null, null);
        }

        // 11. Check Nearby Player Greeting (when player is within 6.5 blocks)
        if (nearestPlayerDistSqr <= 42.25D && world.random.nextFloat() < 0.30F) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.PLAYER_GREETING, nearestPlayer, null, null);
        }

        // 12. Discover nearby fellow citizens & patrolling guards for social/reassurance interactions
        String nearbyGuardName = null;
        String nearbyFriendName = null;
        if (entity != null) {
            final List<EntityCitizen> nearbyCitizens = world.getEntitiesOfClass(
                    EntityCitizen.class,
                    entity.getBoundingBox().inflate(11.0D),
                    other -> other != null && other != entity && other.isAlive());
            for (final EntityCitizen other : nearbyCitizens) {
                final boolean otherIsGuard = other.getCitizenJobHandler() != null
                        && other.getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard<?>;
                if (otherIsGuard && nearbyGuardName == null) {
                    nearbyGuardName = other.getName().getString();
                } else if (!otherIsGuard && nearbyFriendName == null) {
                    nearbyFriendName = other.getName().getString();
                }
            }
        }

        final int effectiveProfile = resolveEffectiveProfileIndex(citizen);
        if (world.random.nextFloat() < 0.35F) {
            if (isGuard && nearbyFriendName != null) {
                return new ResolvedVoiceSituation(CitizenVoiceContext.GUARD_REASSURANCE, nearestPlayer, null, nearbyFriendName);
            } else if (!isGuard && nearbyGuardName != null && (effectiveProfile == 0 || effectiveProfile == 2)) {
                return new ResolvedVoiceSituation(CitizenVoiceContext.WORKER_THANKS_GUARD, nearestPlayer, nearbyGuardName, nearbyFriendName);
            }
        }

        // 13. Check Home Building Comfort (ONLY when the citizen is actually inside or within 12 blocks of their home!)
        if (home != null && home.getPosition().distSqr(pos) <= 144.0D && world.random.nextFloat() < 0.30F) {
            if (home.getBuildingLevelEquivalent() <= 2) {
                return new ResolvedVoiceSituation(CitizenVoiceContext.HOME_COMFORT_BAD, nearestPlayer, nearbyGuardName, nearbyFriendName);
            } else if (home.getBuildingLevelEquivalent() >= 4) {
                return new ResolvedVoiceSituation(CitizenVoiceContext.HOME_COMFORT_GOOD, nearestPlayer, nearbyGuardName, nearbyFriendName);
            }
        }

        // 14. Early-morning commute flavor (0..2200 ticks)
        final long dayTime = world.getDayTime() % 24000L;
        if (dayTime < 2200L && job != null && world.random.nextFloat() < 0.30F) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.MORNING_COMMUTE, nearestPlayer, nearbyGuardName, nearbyFriendName);
        }

        // 15. Citizen-to-citizen social chatter when passing or hanging out near another colonist
        if (nearbyFriendName != null && (citizenState.contains("LEISURE") || world.random.nextFloat() < 0.25F)) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.SOCIAL_CHATTER, nearestPlayer, nearbyGuardName, nearbyFriendName);
        }

        // 16. Active Job Work vs Leisure / Mood
        if (citizenState.contains("LEISURE")) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.LEISURE_AND_HAPPY, nearestPlayer, nearbyGuardName, nearbyFriendName);
        }

        if (job != null && !citizen.isChild() && (!workerState.isEmpty() && !workerState.equals("IDLE") || isGuard)) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.ACTIVE_JOB_WORK, nearestPlayer, nearbyGuardName, nearbyFriendName);
        }

        final double happiness = (citizen.getCitizenHappinessHandler() != null && colony != null)
                ? citizen.getCitizenHappinessHandler().getHappiness(colony, citizen)
                : 7.0D;
        if (happiness < 4.5D) {
            return new ResolvedVoiceSituation(CitizenVoiceContext.UNHAPPY_MOOD, nearestPlayer, nearbyGuardName, nearbyFriendName);
        }

        return new ResolvedVoiceSituation(CitizenVoiceContext.LEISURE_AND_HAPPY, nearestPlayer, nearbyGuardName, nearbyFriendName);
    }

    /**
     * Selects a synchronized {@code (soundEventKey, spokenText)} pair for the citizen's exact voice profile
     * and broadcasts the {@link CitizenVoicelinePayload} to nearby players.
     */
    private static void dispatchAudioAndSpeechBubble(
            @NotNull final ServerLevel world,
            @NotNull final BlockPos pos,
            @NotNull final ICitizenData citizen,
            @NotNull final ResolvedVoiceSituation situation,
            @Nullable final EventType explicitEventHint) {

        final AbstractCivilianEntity entity = citizen.getEntity().orElse(null);
        if (entity == null || !entity.isAlive()) {
            return;
        }

        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        final boolean playAudio = cfg.enableSmartSoundEventRouting.get();
        final boolean showBubble = cfg.enableFloatingSpeechBubbles.get();
        if (!playAudio && !showBubble) {
            return;
        }

        final SynchronizedVoiceLine selected = selectSynchronizedVoiceLine(
                world, citizen, situation, explicitEventHint);
        if (selected.spokenText().isEmpty() && selected.soundEventKey().isEmpty()) {
            return;
        }

        final String jobName = resolveJobDisplayName(citizen);
        final int durationSeconds = Math.max(2, Math.min(20, cfg.speechBubbleDurationSeconds.get()));
        final float pitch = citizen.isChild() ? 1.15F : 1.0F;

        final CitizenVoicelinePayload payload = new CitizenVoicelinePayload(
                entity.getId(),
                citizen.getName(),
                jobName,
                showBubble ? selected.spokenText() : "",
                situation.context().getAccentColorRgb(),
                durationSeconds * 20,
                playAudio ? selected.soundEventKey() : "",
                pitch);

        final int maxRange = Math.max(8, Math.min(64, cfg.voicelineMaxRangeBlocks.get()));
        final double maxRangeSqr = (double) maxRange * maxRange;

        for (final ServerPlayer player : world.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= maxRangeSqr) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /**
     * Resolves the effective voice profile index ({@code 0..3}) used by a citizen.
     * Children map to young-sounding profiles {@code 2} ({@code male3/female3}) or {@code 1} ({@code male2/female2}).
     */
    private static int resolveEffectiveProfileIndex(@NotNull final ICitizenData citizen) {
        final int rawProfile = Math.floorMod(citizen.getVoiceProfile(), 4);
        if (citizen.isChild()) {
            return (rawProfile % 2 == 0) ? 2 : 1;
        }
        return rawProfile;
    }

    /**
     * Resolves the voice directory key ({@code male1..male4}, {@code female1..female4}) for a citizen.
     */
    @NotNull
    private static String resolveVoiceKey(@NotNull final ICitizenData citizen) {
        final int profileIdx = resolveEffectiveProfileIndex(citizen);
        return (citizen.isFemale() ? "female" : "male") + (profileIdx + 1);
    }

    /**
     * Resolves the canonical MineColonies role identifier for a citizen (e.g. {@code builder}, {@code knight}, {@code child}).
     */
    @NotNull
    private static String resolveRoleKey(@NotNull final ICitizenData citizen) {
        if (citizen.isChild()) {
            return "child";
        }
        final IJob<?> job = citizen.getJob();
        if (job != null && job.getJobRegistryEntry() != null && job.getJobRegistryEntry().getKey() != null) {
            return job.getJobRegistryEntry().getKey().getPath().toLowerCase(Locale.ROOT);
        }
        return "unemployed";
    }

    /**
     * Returns the {@code job_<family>_01} clip candidate if valid for the citizen's specific role and voice profile.
     */
    @Nullable
    private static SynchronizedVoiceLine getValidJobClipForCitizen(
            @NotNull final String roleKey,
            @NotNull final String voiceKey,
            final int profileIdx) {

        final String family = ROLE_TO_JOB_FAMILY.get(roleKey);
        if (family == null) {
            return null;
        }
        final Set<Integer> allowedProfiles = ROLE_ALLOWED_JOB_PROFILES.get(roleKey);
        if (allowedProfiles != null && !allowedProfiles.contains(profileIdx)) {
            return null;
        }
        final List<String> familyTexts = JOB_FAMILY_CLIP_TEXTS.get(family);
        if (familyTexts == null || profileIdx < 0 || profileIdx >= familyTexts.size()) {
            return null;
        }
        final String clipName = "job_" + family + "_01";
        return new SynchronizedVoiceLine("voice." + voiceKey + "." + clipName, familyTexts.get(profileIdx));
    }

    /**
     * Helper to build a {@link SynchronizedVoiceLine} for a standard event clip ({@code <eventId>_01} or {@code <eventId>_02}).
     */
    @Nullable
    private static SynchronizedVoiceLine buildEventClip(
            @NotNull final String voiceKey,
            final int profileIdx,
            @NotNull final String eventId,
            final int variantOneBased) {

        final List<String> texts = EVENT_CLIP_TEXTS.get(eventId);
        final int idx = profileIdx * 2 + (variantOneBased - 1);
        if (texts == null || idx < 0 || idx >= texts.size()) {
            return null;
        }
        final String clipName = eventId + "_0" + variantOneBased;
        return new SynchronizedVoiceLine("voice." + voiceKey + "." + clipName, texts.get(idx));
    }

    private static void addIfNotNull(
            @NotNull final List<SynchronizedVoiceLine> list,
            @Nullable final SynchronizedVoiceLine clip) {
        if (clip != null) {
            list.add(clip);
        }
    }

    /**
     * Selects a role-, profile-, and context-verified {@link SynchronizedVoiceLine} so that the
     * spoken {@code .ogg} audio and floating 3D speech bubble text are 100% identical.
     */
    @NotNull
    private static SynchronizedVoiceLine selectSynchronizedVoiceLine(
            @NotNull final ServerLevel world,
            @NotNull final ICitizenData citizen,
            @NotNull final ResolvedVoiceSituation situation,
            @Nullable final EventType explicitEventHint) {

        final int profileIdx = resolveEffectiveProfileIndex(citizen);
        final String voiceKey = resolveVoiceKey(citizen);
        final String roleKey = resolveRoleKey(citizen);
        final CitizenVoiceContext context = situation.context();
        final SynchronizedVoiceLine jobClip = getValidJobClipForCitizen(roleKey, voiceKey, profileIdx);

        final List<SynchronizedVoiceLine> candidates = new ArrayList<>();

        switch (context) {
            case GUARD_COMBAT -> {
                if (profileIdx == 3) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "danger", 2));
                } else {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "danger", 1));
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "danger", 2));
                }
                addIfNotNull(candidates, jobClip);
            }
            case RAID_COMBAT -> {
                if (profileIdx == 2) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "danger", 2));
                } else if (profileIdx == 3) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "danger", 1));
                } else {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "danger", 1));
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "danger", 2));
                }
            }
            case GUARD_RETREAT -> {
                if (profileIdx == 2 || profileIdx == 3) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "danger", 2));
                } else {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "danger", 1));
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "danger", 2));
                }
            }
            case SICK_OR_INJURED -> {
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "sick", 1));
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "sick", 2));
            }
            case MOURNING -> {
                if (profileIdx == 2) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "unhappy", 1));
                } else {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "unhappy", 1));
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "unhappy", 2));
                }
            }
            case UNHAPPY_MOOD -> {
                if (profileIdx == 2) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "unhappy", 2));
                } else {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "unhappy", 1));
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "unhappy", 2));
                }
            }
            case SLEEPING_OR_BEDTIME -> {
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "gotobed", 1));
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "gotobed", 2));
            }
            case HUNGRY -> {
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "lowsaturation", 1));
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "lowsaturation", 2));
            }
            case DINING_SATISFIED -> {
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "highsaturation", 1));
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "highsaturation", 2));
            }
            case RAIN_AND_STORM -> {
                if (profileIdx == 2) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "badweather", 2));
                } else {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "badweather", 1));
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "badweather", 2));
                }
            }
            case THUNDERSTORM -> {
                if (profileIdx == 2) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "badweather", 1));
                } else {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "badweather", 1));
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "badweather", 2));
                }
            }
            case MISSING_TOOLS_OR_MATERIALS -> {
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "missingequipment", 1));
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "missingequipment", 2));
            }
            case HOME_COMFORT_GOOD -> {
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "goodhousing", 1));
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "goodhousing", 2));
            }
            case HOME_COMFORT_BAD -> {
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "badhousing", 1));
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "badhousing", 2));
            }
            case GUARD_REASSURANCE -> {
                if (profileIdx == 1 || profileIdx == 3) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "interaction", 1));
                }
                addIfNotNull(candidates, jobClip);
                if (candidates.isEmpty()) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "general", 1));
                }
            }
            case WORKER_THANKS_GUARD -> {
                if (profileIdx == 0) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "interaction", 2));
                } else if (profileIdx == 2) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "interaction", 1));
                } else {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "interaction", 2));
                }
            }
            case SOCIAL_CHATTER -> {
                if (profileIdx == 0) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "interaction", 1));
                } else {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "interaction", 2));
                }
            }
            case PLAYER_GREETING -> {
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "greeting", 1));
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "greeting", 2));
            }
            case PLAYER_FAREWELL -> {
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "farewell", 1));
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "farewell", 2));
            }
            case MORNING_COMMUTE -> {
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "general", 1));
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "general", 2));
                addIfNotNull(candidates, jobClip);
            }
            case ACTIVE_JOB_WORK -> {
                if (explicitEventHint == EventType.NOISE) {
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "noise", 1));
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "noise", 2));
                    addIfNotNull(candidates, jobClip);
                } else {
                    // Weight job-specific voice line higher so workers proudly speak about their craft
                    addIfNotNull(candidates, jobClip);
                    addIfNotNull(candidates, jobClip);
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "general", 1));
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "general", 2));
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "success", 1));
                    addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "success", 2));
                }
            }
            case LEISURE_AND_HAPPY -> {
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "happy", 1));
                addIfNotNull(candidates, buildEventClip(voiceKey, profileIdx, "happy", 2));
            }
        }

        // Merge any user-defined custom templates from config (paired with a valid context sound key)
        final String fallbackSoundKey = candidates.isEmpty()
                ? ("voice." + voiceKey + ".general_01")
                : candidates.get(0).soundEventKey();
        final String colonPrefix = context.name() + ":";
        final String pipePrefix = context.name() + "|";
        for (final String customEntry : AdvancedColoniesConfig.CITIZEN_COMMON.customVoicelineTemplates.get()) {
            if (customEntry == null) {
                continue;
            }
            if (customEntry.regionMatches(true, 0, colonPrefix, 0, colonPrefix.length())) {
                final String rawText = customEntry.substring(colonPrefix.length()).trim();
                if (!rawText.isEmpty()) {
                    candidates.add(new SynchronizedVoiceLine(
                            fallbackSoundKey, formatPlaceholders(world, citizen, situation, rawText)));
                }
            } else if (customEntry.regionMatches(true, 0, pipePrefix, 0, pipePrefix.length())) {
                final String remainder = customEntry.substring(pipePrefix.length());
                final int secondPipe = remainder.indexOf('|');
                if (secondPipe > 0) {
                    final String targetJob = remainder.substring(0, secondPipe).trim().toLowerCase(Locale.ROOT);
                    final String rawText = remainder.substring(secondPipe + 1).trim();
                    if (!rawText.isEmpty() && ("*".equals(targetJob) || roleKey.contains(targetJob))) {
                        candidates.add(new SynchronizedVoiceLine(
                                fallbackSoundKey, formatPlaceholders(world, citizen, situation, rawText)));
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            final SynchronizedVoiceLine fallback = buildEventClip(voiceKey, profileIdx, "general", 1);
            return fallback != null ? fallback : new SynchronizedVoiceLine("", "");
        }

        final int colonyId = citizen.getColony() != null ? citizen.getColony().getID() : 0;
        final long citizenKey = (((long) colonyId) << 32) | (citizen.getId() & 0xFFFFFFFFL);
        final Integer lastIdx = CITIZEN_LAST_TEMPLATE_IDX.get(citizenKey);

        int chosenIdx = world.random.nextInt(candidates.size());
        if (candidates.size() > 1 && lastIdx != null && chosenIdx == lastIdx) {
            chosenIdx = (chosenIdx + 1) % candidates.size();
        }
        CITIZEN_LAST_TEMPLATE_IDX.put(citizenKey, chosenIdx);
        return candidates.get(chosenIdx);
    }

    @NotNull
    private static String formatPlaceholders(
            @NotNull final ServerLevel world,
            @NotNull final ICitizenData citizen,
            @NotNull final ResolvedVoiceSituation situation,
            @NotNull final String template) {

        final String citizenName = citizen.getName();
        final String jobName = resolveJobDisplayName(citizen);
        final String buildingName = resolveBuildingDisplayName(citizen);
        final int buildingLevel = resolveBuildingLevel(citizen);
        final String colonyName = (citizen.getColony() != null && citizen.getColony().getName() != null && !citizen.getColony().getName().isBlank())
                ? citizen.getColony().getName()
                : "our Colony";
        final String playerName = situation.nearestPlayer() != null
                ? situation.nearestPlayer().getName().getString()
                : "Mayor";
        final String guardName = situation.nearbyGuardName() != null
                ? situation.nearbyGuardName()
                : "the Guard";
        final String friendName = situation.nearbyFriendName() != null
                ? situation.nearbyFriendName()
                : "neighbor";
        final String weatherName = world.isThundering() ? "thunderstorm" : (world.isRaining() ? "rain" : "clear sky");
        final long dayTime = world.getDayTime() % 24000L;
        final String timeOfDay = dayTime < 6000L
                ? "morning"
                : (dayTime < 12000L ? "afternoon" : (dayTime < 13800L ? "evening" : "night"));

        return template
                .replace("{citizen}", citizenName)
                .replace("{job}", jobName)
                .replace("{building}", buildingName)
                .replace("{building_level}", String.valueOf(buildingLevel))
                .replace("{colony}", colonyName)
                .replace("{player}", playerName)
                .replace("{guard}", guardName)
                .replace("{friend}", friendName)
                .replace("{weather}", weatherName)
                .replace("{time}", timeOfDay);
    }

    @NotNull
    private static String resolveJobDisplayName(@NotNull final ICitizenData citizen) {
        if (citizen.isChild()) {
            return "Child";
        }
        final IJob<?> job = citizen.getJob();
        if (job == null || job.getJobRegistryEntry() == null) {
            return "Colonist";
        }
        if (job.getJobRegistryEntry().getTranslationKey() != null) {
            final String translated = Component.translatable(job.getJobRegistryEntry().getTranslationKey()).getString();
            if (!translated.isEmpty() && !translated.startsWith("com.minecolonies")) {
                return translated;
            }
        }
        if (job.getJobRegistryEntry().getKey() != null) {
            final String path = job.getJobRegistryEntry().getKey().getPath();
            if (!path.isEmpty()) {
                return Character.toUpperCase(path.charAt(0)) + path.substring(1);
            }
        }
        return "Worker";
    }

    @NotNull
    private static String resolveBuildingDisplayName(@NotNull final ICitizenData citizen) {
        final IBuilding workBuilding = citizen.getWorkBuilding();
        if (workBuilding != null) {
            final String customName = workBuilding.getCustomName();
            if (customName != null && !customName.isBlank()) {
                return customName;
            }
            final String schematicName = workBuilding.getSchematicName();
            if (schematicName != null && !schematicName.isBlank()) {
                return Character.toUpperCase(schematicName.charAt(0))
                        + schematicName.substring(1).replace('_', ' ');
            }
        }
        final IBuilding homeBuilding = citizen.getHomeBuilding();
        if (homeBuilding != null) {
            return "Residence";
        }
        return "Colony";
    }

    private static int resolveBuildingLevel(@NotNull final ICitizenData citizen) {
        final IBuilding workBuilding = citizen.getWorkBuilding();
        if (workBuilding != null && workBuilding.getBuildingLevel() > 0) {
            return workBuilding.getBuildingLevel();
        }
        final IBuilding homeBuilding = citizen.getHomeBuilding();
        if (homeBuilding != null && homeBuilding.getBuildingLevel() > 0) {
            return homeBuilding.getBuildingLevel();
        }
        return 1;
    }

    private record SynchronizedVoiceLine(
            @NotNull String soundEventKey,
            @NotNull String spokenText) {
    }

    private record ResolvedVoiceSituation(
            @NotNull CitizenVoiceContext context,
            @Nullable Player nearestPlayer,
            @Nullable String nearbyGuardName,
            @Nullable String nearbyFriendName) {
    }
}
