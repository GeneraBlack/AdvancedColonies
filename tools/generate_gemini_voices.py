import argparse
import asyncio
import json
import os
import subprocess
import wave
from pathlib import Path

from google import genai
from google.genai import types

PROJECT_ROOT = Path(r"d:\projekt\AdvancedColonies")
SOUNDS_ROOT = PROJECT_ROOT / "src" / "main" / "resources" / "assets" / "advancedcolonies" / "sounds" / "voice"
ADV_SOUNDS_JSON = PROJECT_ROOT / "src" / "main" / "resources" / "assets" / "advancedcolonies" / "sounds.json"
MC_SOUNDS_JSON = PROJECT_ROOT / "src" / "main" / "resources" / "assets" / "minecolonies" / "sounds.json"

LIVE_MODELS = [
    "gemini-3.8-live",
    "gemini-3.1-flash-live-preview",
    "gemini-2.5-flash-native-audio-latest",
]

SYSTEM_INSTRUCTION = (
    "You are a professional voice actor for a medieval fantasy colony building game (MineColonies). "
    "For every user message, perform ONLY the exact dialogue line after the colon, acting out the "
    "specified emotion with 100% conviction, natural vocal dynamics, and realism. "
    "Never speak the emotion description itself, and never add any extra words before or after the line."
)

# 8 distinct Google Gemini 3.8 Live voices mapped to MineColonies' 8 citizen voice profiles
VOICES = {
    "male1":   {"gemini_voice": "Puck",   "persona": "Lively, expressive townsman"},
    "male2":   {"gemini_voice": "Charon", "persona": "Deep, grounded craftsman"},
    "male3":   {"gemini_voice": "Fenrir", "persona": "Energetic, bold colonist"},
    "male4":   {"gemini_voice": "Orus",   "persona": "Sturdy, seasoned veteran"},
    "female1": {"gemini_voice": "Kore",   "persona": "Warm, clear townswoman"},
    "female2": {"gemini_voice": "Aoede",  "persona": "Bright, expressive artisan"},
    "female3": {"gemini_voice": "Leda",   "persona": "Gentle, friendly villager"},
    "female4": {"gemini_voice": "Zephyr", "persona": "Spirited, confident worker"},
}

SITUATION_EMOTIONS = {
    "danger": (
        "Panicked, breathless, terrified and urgent shouting during a sudden raid attack on the town"
    ),
    "gotobed": (
        "Exhausted, sleepy, yawning, worn-out nighttime voice at the end of a long shift"
    ),
    "badweather": (
        "Shivering, cold, frustrated and windswept while stuck working outdoors in a freezing downpour"
    ),
    "lowsaturation": (
        "Starving, weary, stomach-rumbling, drained worker longing for a hot meal"
    ),
    "highsaturation": (
        "Deeply satisfied, warm, relieved, and cheerful right after eating a delicious hot meal"
    ),
    "badhousing": (
        "Wistful, mildly disappointed, sighing about a cramped and drafty little cottage"
    ),
    "goodhousing": (
        "Warm, proud, cozy, and deeply grateful for a spacious, comfortable home"
    ),
    "greeting": (
        "Bright, welcoming, upbeat, and genuinely happy to greet a friend"
    ),
    "farewell": (
        "Warm, caring, encouraging, and friendly while wishing someone safe travels"
    ),
    "missingequipment": (
        "Frustrated, impatient, and stressed craftsman stuck waiting for missing tools or materials"
    ),
    "happy": (
        "Joyful, smiling, enthusiastic, and high-spirited while enjoying a thriving town"
    ),
    "unhappy": (
        "Heavy-hearted, melancholic, discouraged, and sighing softly after rough days or mourning"
    ),
    "sick": (
        "Weak, feverish, congested, shaky, and exhausted villager suffering from a bad illness"
    ),
    "interaction": (
        "Warm, neighborly, conversational, and appreciative while chatting with a fellow colonist or Guard"
    ),
    "success": (
        "Proud, energetic, accomplished, and beaming with satisfaction after finishing a masterpiece"
    ),
    "general": (
        "Engaged, natural, lively, and focused while going about daily town work"
    ),
    "noise": (
        "Thoughtful, casual, muttering naturally under the breath while measuring and working"
    ),
    "job": (
        "Passionate, proud, expressive medieval artisan enthusiastically immersed in their craft"
    ),
}

EVENT_LINES = {
    "danger": [
        "Raiders in the colony! Everyone get inside and lock the doors, now!",
        "We're under attack! Stay alert—get behind the Guards immediately!",
        "Hostiles spotted right outside! Sound the alarm, hurry!",
        "Monsters are closing in on the perimeter! Guards, to the front!",
        "To arms! Don't let those raiders breach our defenses!",
        "Watch out! There's fighting breaking out right near the workshops!",
        "Drop your tools and run for cover! The colony is under attack!",
        "Hold the line! Keep those hostiles away from the town center!",
    ],
    "gotobed": [
        "Ugh, what a long shift... time to finally head home and get some sleep.",
        "The sun's down and my feet are aching... heading off to bed.",
        "Barely keeping my eyes open... my bed is definitely calling my name.",
        "Exhausted from head to toe... goodnight, everyone.",
        "Locking up the workshop for the night... time for some well-earned rest.",
        "The night watch belongs to the Guards now... I'm finally off to sleep.",
        "Stars are out and I'm completely worn out... see you all tomorrow.",
        "Time to hang up my tools and call it a night...",
    ],
    "badweather": [
        "Brrr! Working out in this freezing rain is soaking my clothes right through!",
        "Ugh, looks like these storm clouds aren't letting up anytime soon!",
        "My boots are squelching in the cold mud with every single step...",
        "At least the Farmer's crops will love this rain, even if I'm freezing out here!",
        "Did you hear that thunder?! Hope our rooftops hold up against this storm!",
        "Cold water's dripping right down my collar—what a miserable downpour!",
        "Careful on the paths, everyone! The cobblestones are super slick from the rain!",
        "Shivering out here... a hot bowl of soup by the fire can't come soon enough!",
    ],
    "lowsaturation": [
        "Ugh, my stomach is growling so loud... I really need a hot meal at the Restaurant.",
        "Hours of hard labor really drains you... I'm starving.",
        "Can't focus on an empty stomach anymore... heading off to find some food.",
        "I'm so hungry I could eat a whole loaf of bread right about now...",
        "My arms are feeling shaky from hunger... time to grab a bite to eat.",
        "Been working non-stop without a break... really need to refuel soon.",
        "Hope the Cook still has something warm simmering on the stove...",
        "A starving worker is a clumsy worker... better go eat before I drop.",
    ],
    "highsaturation": [
        "Ahh, nothing beats a warm, hearty meal after a long shift!",
        "Compliments to the Cook! That hot meal really hit the spot!",
        "Well fed and full of energy again—ready to get right back to work!",
        "Now that was delicious! The food in our colony keeps getting better and better!",
        "A full belly and a warm drink—makes the whole workday fly right by!",
        "Whoever baked the bread today deserves a medal! I feel great!",
        "That rich stew warmed me right up! Back to my duties with a smile!",
        "Fully recharged and feeling fantastic! Ready for the next task!",
    ],
    "badhousing": [
        "Sigh... my residence is getting so cramped. Hopefully the Builder upgrades it soon.",
        "A slightly bigger house would make resting after a hard shift much nicer...",
        "It's a humble roof over my head, though a proper upgrade wouldn't hurt.",
        "The floorboards creak and there's a cold draft by the door... wish it were cozier.",
        "Could really use a little more room and a softer bed at home...",
        "Our little cottage is really showing its age compared to the rest of town.",
        "Maybe once the Builders finish their current project, they can fix up our street...",
        "Hoping the Mayor puts in a work order to expand our cramped house soon.",
    ],
    "goodhousing": [
        "It feels wonderful having such a spacious, comfortable home to return to!",
        "Our residences have improved so much—living in this colony is a true joy!",
        "Home sweet home! A cozy, warm house makes all the hard work worthwhile!",
        "Warm hearth, sturdy walls, and a dry roof—the Builders truly outdid themselves!",
        "Having a proper bedroom and fine furniture makes resting feel amazing!",
        "Just look at the craftsmanship on our houses now! The colony looks incredible!",
        "My home is so cozy and inviting, I almost don't want to leave in the morning!",
        "Clean floors, warm lantern light, and plenty of space—couldn't ask for a better home!",
    ],
    "greeting": [
        "Hello there! Everything at the workshop is coming along wonderfully today!",
        "Hey! Always so glad to see you checking in on our colony!",
        "Good day to you! Just tending to my duties and keeping things running smoothly!",
        "Welcome back! Let me know if you need anything at all from my hut!",
        "Great to see you out and about! The colony is thriving under your leadership!",
        "Hello, my friend! Hope your travels outside the colony have been safe!",
        "At your service! Proud to do my part for the town today!",
        "What a fine day, isn't it? Everything's shipshape over here!",
    ],
    "farewell": [
        "Safe travels out there! We'll keep the colony running strong while you're away!",
        "Take care of yourself! See you next time you stop by our district!",
        "Until next time! I'll get right back to my tasks!",
        "Watch your step out in the wilds—come back to us safe and sound!",
        "Good luck on your journey! We've got everything covered here in town!",
        "See you later! Have a wonderful rest of your day!",
        "Farewell for now! Don't be a stranger!",
        "Back to the craft for me—catch you later, friend!",
    ],
    "missingequipment": [
        "Tsk, stuck waiting on fresh supplies before I can continue my work here!",
        "I'm completely stalled without the right tools at my workbench right now!",
        "Come on, where is that Courier? We urgently need those materials from the Warehouse!",
        "Can't do proper work bare-handed! I've put in an urgent request for new gear!",
        "Everything's prepped, but I'm missing the equipment to actually finish the job!",
        "Checked the storage racks twice, and we're totally out of stock! How frustrating!",
        "My old tool finally snapped in half... standing by until a replacement arrives!",
        "Hands are tied until the next delivery gets here—we're out of materials!",
    ],
    "happy": [
        "Haha, what a wonderful day! Just taking a moment to enjoy the lively atmosphere!",
        "It's honestly so inspiring to see how much our town has grown and prospered!",
        "Life in this colony treats us so well—great friends and a real community!",
        "Just stretching my legs with a big smile—our town looks better every single day!",
        "You can really feel the pride and joy everyone takes in our colony!",
        "Moments like this make all the busy shifts at the workshop completely worth it!",
        "Looking around at our bustling streets... we've built something truly special here!",
        "Morale is sky-high today! Can't help but smile seeing everyone thriving!",
    ],
    "unhappy": [
        "Sigh... it's been a really rough few days lately... hope things look up soon.",
        "Feeling so worn down and heavy-hearted today... but I'll try to keep going.",
        "Could really use a little bit of good news around town for a change...",
        "Everything feels twice as hard today... my spirits are pretty low.",
        "Still carrying a heavy heart after what we've been through... rest in peace, old friend.",
        "Things have been so stressful lately... just praying for quieter, happier days ahead.",
        "Barely have the heart to focus today... the colony feels so somber.",
        "Hoping tomorrow brings a little warmth back, because today has been tough...",
    ],
    "sick": [
        "Ugh... feeling so feverish all morning... I really need to see the Healer at the Hospital.",
        "My head is spinning and I'm burning up... hope the Hospital has the medicine I need.",
        "Can't stop coughing today... my chest hurts, I shouldn't push myself too hard.",
        "Shivering with this awful chill... off to the Hospital before I collapse.",
        "My throat feels like sandpaper and my knees are weak... Healer, please help...",
        "Every single step feels like lead today... definitely caught a nasty fever.",
        "Better get to a Hospital bed quickly before I pass this sickness to anyone else...",
        "Not feeling like myself at all... dizzy, weak, and in need of a cure.",
    ],
    "interaction": [
        "Hey there, neighbor! Always great seeing a friendly face—let's catch up at the Tavern later!",
        "Good to see the Guards patrolling our street! Makes working here feel so much safer!",
        "Sector is all clear, friends! You can all focus on your craft in complete peace!",
        "How's your shift going today? Save me a seat at the dining hall later!",
        "Thanks for keeping watch over us, Guard! The whole neighborhood appreciates you!",
        "Working alongside good friends like you is the best part of living in this colony!",
        "Patrol check complete! Not a single monster getting near our workshops today!",
        "Appreciate the help, neighbor! Together we keep this town running strong!",
    ],
    "success": [
        "Yes! Another task finished cleanly! Look at that craftsmanship!",
        "Haha, nailed it! Today's work is turning out better than ever!",
        "Checking off one job after another—what an awesome, productive shift!",
        "That's how a master artisan does it! Another batch completed for the colony!",
        "Nothing more satisfying than seeing a hard day's work pay off this well!",
        "Right on target! Output at the workshop is booming today!",
        "That came together beautifully! Bring on the next work order!",
        "Boom! Another milestone finished for our growing town!",
    ],
    "general": [
        "Fresh air and a busy workshop—ready to make today count for the colony!",
        "Keeping a steady rhythm and doing my part to help our town flourish!",
        "Let's see what's next on the workboard—plenty of good work ahead!",
        "Focused and in the zone! Everything at the hut is running smoothly!",
        "Step by step, keeping the colony's production humming right along!",
        "Always something exciting being built or crafted around town!",
        "Checking the supplies and diving right into the next task!",
        "Proud to ply my trade here—let's keep the momentum going!",
    ],
    "noise": [
        "Hmm, let's see how this lines up...",
        "Right then, steady does it...",
        "Almost got it... just a little further.",
        "Let's double-check that measurement real quick.",
        "One careful step at a time...",
        "Aha, looking good so far!",
        "Alright, what's next on the workbench?",
        "There we go—fits right into place!",
    ],
}

JOB_FAMILY_LINES = {
    "builder": [
        "Measuring twice and placing once—this new structure is really taking shape!",
        "Brick by brick and beam by beam, another grand project for the colony comes together!",
        "Checking the blueprints again—wait until the Mayor sees this building finished!",
        "Laying solid foundations and raising the walls! A Builder's pride stands for generations!",
    ],
    "farmer": [
        "The soil looks rich and fertile today—this harvest is going to fill the Warehouse to the brim!",
        "Tending the fields and planting fresh seeds while the sun shines bright!",
        "Look at those golden crops! Nobody in our colony is going hungry on my watch!",
        "Fresh produce straight from our fields to the kitchen—what a bountiful season!",
    ],
    "miner": [
        "The rock strata down here are tough, but striking a rich ore vein makes every swing worth it!",
        "Reinforcing the shaft timbers and digging deeper—plenty of iron and coal coming up today!",
        "Placing fresh torches along the tunnel and hauling up treasure from the deep!",
        "Listen to the ring of my pickaxe—there's a rich seam of ore right behind this rock!",
    ],
    "lumberjack": [
        "Timber! Watch your step—another tall trunk coming down cleanly for the Sawmill!",
        "Felling mature timber and replanting saplings so our forest stays green and lush!",
        "Freshly cut logs stacked high and ready for our Builders and Carpenters!",
        "Nothing beats the crisp rhythm of a sharp axe out in the woods!",
    ],
    "deliveryman": [
        "Special delivery coming through! Keeping supplies flowing fast between the Warehouse and huts!",
        "My pack is full and my boots are flying—half the town is counting on this delivery!",
        "Sorting crates and running batches across town! Logistics is the heartbeat of the colony!",
        "Fast feet and optimized routes! Another round of high-priority deliveries done!",
    ],
    "cook": [
        "The ovens are blazing hot and delicious meals are simmering in the kitchen!",
        "Kneading fresh dough and roasting meat—come on in while the food is piping hot!",
        "Keeping the dining tables stocked so every worker in town leaves with a full belly!",
        "Smells incredible in the kitchen today—just added the secret seasoning!",
    ],
    "healer": [
        "Checking on my patients and preparing fresh herbal remedies here at the Hospital!",
        "Clean bandages, soothing tonics, and a caring hand—nobody stays sick for long on my watch!",
        "Crushing medicinal herbs in the mortar so we're ready for any illness or injury!",
        "A healthy colony is a happy colony—everyone's recovery is right on track!",
    ],
    "researcher": [
        "Eureka! Cross-referencing these ancient scrolls—we're on the verge of a major breakthrough!",
        "Every chapter studied at the University unlocks powerful new knowledge for our people!",
        "Dipping my quill and documenting a brand-new discovery for the colony!",
        "Knowledge is power! Our research today will make the entire town stronger tomorrow!",
    ],
    "enchanter": [
        "Channeling pure arcane mana into fresh tomes—feel how strongly the altar resonates!",
        "Inscribing glowing runes of protection and efficiency for our colony's workers!",
        "Careful with the lapis and ancient syllables—binding powerful magic into this book!",
        "The mystic energy in the tower is humming today—another enchanted tome complete!",
    ],
    "blacksmith": [
        "Strike while the iron is glowing red-hot! Forging the finest tools and armor in the land!",
        "The ring of the anvil and the hiss of quenched steel—that's music to a Blacksmith's ears!",
        "Stoking the roaring blast furnaces to turn raw ore into gleaming metal ingots!",
        "Tempering blades and hammering plate armor so our Guards are unstoppable!",
    ],
    "carpenter": [
        "Sawdust in the air and the buzz of the blade—turning rough logs into precision planks!",
        "Chiseling fine stone bricks and cutting timber frames for our colony's grand projects!",
        "Mixing and curing rock-solid batches of concrete and masonry for the Builders!",
        "Steady hands and fine craftsmanship—every joint and block fits together like clockwork!",
    ],
    "herder": [
        "The animals in the pens are healthy, calm, and thriving under our care today!",
        "Fresh milk, thick wool, golden honeycombs, and eggs—our pastures are overflowing!",
        "Gentle puff of smoke for the beehives and fresh wheat in the troughs—livestock work is a joy!",
        "Reeling in fresh fish and tending the herds to keep our colony's larders full!",
    ],
    "guard": [
        "Eyes sharp, shield high, blade ready—keeping my assigned patrol sector locked down!",
        "Scanning the horizon along my patrol route! No monsters or raiders getting past me!",
        "Standing vigilant watch so every citizen in our colony can work and live in peace!",
        "Spreading our patrol coverage across every street—this district is safe under my watch!",
    ],
}

ROLE_TO_JOB_FAMILY = {
    "alchemist": None,
    "archertraining": "guard",
    "baker": "cook",
    "beekeeper": "herder",
    "blacksmith": "blacksmith",
    "builder": "builder",
    "cavalry": "guard",
    "chef": "cook",
    "chickenherder": "herder",
    "child": None,
    "combattraining": "guard",
    "composter": "farmer",
    "concretemixer": "carpenter",
    "cook": "cook",
    "cookassistant": "cook",
    "cowboy": "herder",
    "crusher": "carpenter",
    "deliveryman": "deliveryman",
    "druid": "guard",
    "dyer": "carpenter",
    "enchanter": "enchanter",
    "farmer": "farmer",
    "fisherman": "herder",
    "fletcher": "carpenter",
    "florist": "farmer",
    "glassblower": "blacksmith",
    "healer": "healer",
    "knight": "guard",
    "lumberjack": "lumberjack",
    "mechanic": "carpenter",
    "miner": "miner",
    "netherworker": "miner",
    "planter": "farmer",
    "pupil": "researcher",
    "quarrier": "miner",
    "rabbitherder": "herder",
    "ranger": "guard",
    "researcher": "researcher",
    "sawmill": "carpenter",
    "shepherd": "herder",
    "sifter": "carpenter",
    "smelter": "blacksmith",
    "stablemaster": "herder",
    "stonemason": "carpenter",
    "stonesmeltery": "blacksmith",
    "student": "researcher",
    "swineherder": "herder",
    "teacher": "researcher",
    "undertaker": None,
    "unemployed": None,
    "visitor": None,
}

# Restricts which voice profile indices (0..3) can use the family's job_<family>_01 clip for a specific role
ROLE_ALLOWED_JOB_PROFILES = {
    "composter": {0},
    "florist": {0, 1},
    "netherworker": {2},
    "pupil": {2, 3},
    "student": {2, 3},
    "teacher": {2, 3},
    "smelter": {2},
    "stonesmeltery": {2},
    "glassblower": {2},
    "sawmill": {0, 1, 3},
    "stonemason": {1, 2, 3},
    "concretemixer": {2, 3},
    "fletcher": {0, 3},
    "mechanic": {3},
    "dyer": {3},
    "crusher": {3},
    "sifter": {3},
    "beekeeper": {1, 2},
    "cowboy": {0, 1, 2},
    "shepherd": {0, 1, 2},
    "chickenherder": {0, 1, 2},
    "swineherder": {0, 2},
    "rabbitherder": {0, 2},
    "stablemaster": {0, 2},
    "fisherman": {3},
}


def get_valid_job_clips(role: str, family: str | None, voice_key: str, profile_num: int, job_sound_map: dict) -> list[str]:
    if family is None:
        return []
    allowed_profiles = ROLE_ALLOWED_JOB_PROFILES.get(role, {0, 1, 2, 3})
    if profile_num not in allowed_profiles:
        return []
    return list(job_sound_map.get((voice_key, family), []))


def get_filtered_event_clips(
    role: str,
    family: str | None,
    voice_key: str,
    profile_num: int,
    canonical_event: str,
    event_sound_map: dict,
    job_sound_map: dict,
) -> list[str]:
    is_guard = family == "guard"
    c1 = f"advancedcolonies:voice/{voice_key}/{canonical_event}_01"
    c2 = f"advancedcolonies:voice/{voice_key}/{canonical_event}_02"

    if canonical_event == "danger":
        if is_guard:
            if profile_num == 3:
                return [c2]
            return [c1, c2]
        else:
            if profile_num == 2:
                return [c2]
            if profile_num == 3:
                return [c1]
            return [c1, c2]

    if canonical_event == "interaction":
        if is_guard:
            if profile_num == 0:
                return [c1] + get_valid_job_clips(role, family, voice_key, profile_num, job_sound_map)
            if profile_num == 1:
                return [c1, c2]
            if profile_num == 2:
                return [c2] + get_valid_job_clips(role, family, voice_key, profile_num, job_sound_map)
            return [c1, c2]
        else:
            if profile_num == 0:
                return [c1, c2]
            if profile_num == 1:
                return [c2]
            if profile_num == 2:
                return [c1, c2]
            return [c2]

    if canonical_event == "unhappy":
        if profile_num == 2:
            return [c2]
        return [c1, c2]

    return list(event_sound_map[(voice_key, canonical_event)])


def pcm_to_ogg_sync(pcm_bytes: bytes, ogg_path: Path) -> None:
    wav_path = ogg_path.with_suffix(".tmp.wav")
    with wave.open(str(wav_path), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(24000)
        wf.writeframes(pcm_bytes)

    subprocess.run(
        [
            "ffmpeg", "-y",
            "-i", str(wav_path),
            "-af", "silenceremove=start_periods=1:start_silence=0.04:start_threshold=-42dB",
            "-ac", "1",
            "-ar", "24000",
            "-c:a", "libvorbis",
            "-q:a", "3",
            str(ogg_path),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    if wav_path.exists():
        wav_path.unlink()


async def synthesize_voice_profile_live(
    client: genai.Client,
    voice_key: str,
    clip_specs: list[tuple[str, str, str]],
    force: bool,
    progress_counter: list[int],
    total_clips: int,
) -> None:
    """
    Streams all clips for a single citizen voice profile over chunked Gemini 3.8 Live WebSocket sessions.
    Each item in clip_specs is (clip_name, emotion_key, text).
    """
    out_dir = SOUNDS_ROOT / voice_key
    out_dir.mkdir(parents=True, exist_ok=True)

    pending = []
    for clip_name, emotion_key, text in clip_specs:
        ogg_path = out_dir / f"{clip_name}.ogg"
        if not force and ogg_path.exists() and ogg_path.stat().st_size > 2000:
            progress_counter[0] += 1
        else:
            pending.append((clip_name, emotion_key, text, ogg_path))

    if not pending:
        print(f"[{voice_key}] All {len(clip_specs)} clips already up-to-date.")
        return

    voice_cfg = VOICES[voice_key]
    chunk_size = 10

    for chunk_start in range(0, len(pending), chunk_size):
        chunk = pending[chunk_start:chunk_start + chunk_size]
        idx = 0
        attempt = 0

        while idx < len(chunk):
            model_name = LIVE_MODELS[attempt % len(LIVE_MODELS)]
            live_config = types.LiveConnectConfig(
                response_modalities=["AUDIO"],
                system_instruction=types.Content(
                    parts=[types.Part.from_text(text=f"{SYSTEM_INSTRUCTION} Your character persona: {voice_cfg['persona']}.")]
                ),
                speech_config=types.SpeechConfig(
                    voice_config=types.VoiceConfig(
                        prebuilt_voice_config=types.PrebuiltVoiceConfig(
                            voice_name=voice_cfg["gemini_voice"]
                        )
                    )
                ),
            )
            try:
                async with client.aio.live.connect(model=model_name, config=live_config) as session:
                    while idx < len(chunk):
                        clip_name, emotion_key, text, ogg_path = chunk[idx]
                        emo_desc = SITUATION_EMOTIONS[emotion_key]
                        prompt_text = f"Emotion ({emo_desc}): {text}"

                        await session.send_client_content(
                            turns=types.Content(
                                role="user",
                                parts=[types.Part.from_text(text=prompt_text)],
                            ),
                            turn_complete=True,
                        )

                        audio_bytes = bytearray()
                        async for response in session.receive():
                            if response.data:
                                audio_bytes.extend(response.data)
                            if response.server_content and response.server_content.turn_complete:
                                break

                        if len(audio_bytes) < 4000:
                            raise RuntimeError(f"Short audio buffer ({len(audio_bytes)} bytes) on {voice_key}/{clip_name}")

                        await asyncio.to_thread(pcm_to_ogg_sync, bytes(audio_bytes), ogg_path)
                        progress_counter[0] += 1
                        idx += 1
                        attempt = 0
                        if progress_counter[0] % 20 == 0 or progress_counter[0] == total_clips:
                            print(f"Progress: {progress_counter[0]}/{total_clips} emotional clips generated ({model_name})...")
            except Exception as exc:
                attempt += 1
                if attempt >= 8:
                    raise RuntimeError(f"[{voice_key}] Failed after 8 attempts at {chunk[idx][0]}: {exc}") from exc
                await asyncio.sleep(1.5 * attempt)


async def main():
    parser = argparse.ArgumentParser(description="Synthesize emotional citizen .ogg voice lines using Gemini 3.8 Live.")
    parser.add_argument("--api-key", default=os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY"),
                        required=False, help="Google Gemini API Key")
    parser.add_argument("--force", action="store_true", help="Overwrite existing .ogg files")
    parser.add_argument("--json-only", action="store_true", help="Only regenerate sounds.json files without calling Gemini API")
    args = parser.parse_args()

    voice_keys = ["male1", "male2", "male3", "male4", "female1", "female2", "female3", "female4"]

    event_sound_map = {}
    job_sound_map = {}
    per_voice_specs: dict[str, list[tuple[str, str, str]]] = {vk: [] for vk in voice_keys}

    for v_idx, voice_key in enumerate(voice_keys):
        profile_num = v_idx % 4
        for event_id, lines in EVENT_LINES.items():
            line1 = lines[(profile_num * 2) % len(lines)]
            line2 = lines[(profile_num * 2 + 1) % len(lines)]
            c1 = f"{event_id}_01"
            c2 = f"{event_id}_02"
            event_sound_map[(voice_key, event_id)] = [
                f"advancedcolonies:voice/{voice_key}/{c1}",
                f"advancedcolonies:voice/{voice_key}/{c2}",
            ]
            per_voice_specs[voice_key].append((c1, event_id, line1))
            per_voice_specs[voice_key].append((c2, event_id, line2))

        for family_id, f_lines in JOB_FAMILY_LINES.items():
            line = f_lines[profile_num % len(f_lines)]
            c_job = f"job_{family_id}_01"
            job_sound_map[(voice_key, family_id)] = [
                f"advancedcolonies:voice/{voice_key}/{c_job}"
            ]
            per_voice_specs[voice_key].append((c_job, "job", line))

    if not args.json_only:
        if not args.api_key:
            raise SystemExit("Please provide --api-key or set GEMINI_API_KEY (or pass --json-only).")

        client = genai.Client(api_key=args.api_key)
        total_clips = sum(len(v) for v in per_voice_specs.values())
        progress_counter = [0]
        print(f"Starting Gemini 3.8 Live native emotional synthesis for {total_clips} .ogg clips across 8 voices...")

        sem = asyncio.Semaphore(4)

        async def run_voice(vk: str):
            async with sem:
                await synthesize_voice_profile_live(
                    client,
                    vk,
                    per_voice_specs[vk],
                    args.force,
                    progress_counter,
                    total_clips,
                )

        await asyncio.gather(*(run_voice(vk) for vk in voice_keys))
        print(f"All {total_clips} Gemini 3.8 Live emotional .ogg files synthesized successfully!")

    adv_sounds = {}
    for (voice_key, event_id), sound_paths in sorted(event_sound_map.items()):
        key = f"voice.{voice_key}.{event_id}"
        adv_sounds[key] = {
            "category": "neutral",
            "sounds": [{"name": sp, "stream": False, "attenuation_distance": 28} for sp in sound_paths],
        }
    for (voice_key, family_id), sound_paths in sorted(job_sound_map.items()):
        key = f"voice.{voice_key}.job.{family_id}"
        adv_sounds[key] = {
            "category": "neutral",
            "sounds": [{"name": sp, "stream": False, "attenuation_distance": 28} for sp in sound_paths],
        }
    # Register every individual clip key (e.g. voice.male1.danger_01, voice.male1.job_builder_01)
    # for 1:1 synchronized audio + 3D speech bubble playback.
    for voice_key, specs in sorted(per_voice_specs.items()):
        for clip_name, _emotion_key, _text in specs:
            clip_key = f"voice.{voice_key}.{clip_name}"
            adv_sounds[clip_key] = {
                "category": "neutral",
                "sounds": [
                    {
                        "name": f"advancedcolonies:voice/{voice_key}/{clip_name}",
                        "stream": False,
                        "attenuation_distance": 28,
                    }
                ],
            }

    ADV_SOUNDS_JSON.parent.mkdir(parents=True, exist_ok=True)
    ADV_SOUNDS_JSON.write_text(json.dumps(adv_sounds, indent=2), encoding="utf-8")

    event_aliases = {
        "badhousing": "badhousing",
        "bad_housing": "badhousing",
        "badweather": "badweather",
        "bad_weather": "badweather",
        "danger": "danger",
        "farewell": "farewell",
        "general": "general",
        "goodhousing": "goodhousing",
        "good_housing": "goodhousing",
        "gotobed": "gotobed",
        "off_to_bed": "gotobed",
        "greeting": "greeting",
        "happy": "happy",
        "highsaturation": "highsaturation",
        "saturation_high": "highsaturation",
        "interaction": "interaction",
        "lowsaturation": "lowsaturation",
        "saturation_low": "lowsaturation",
        "missingequipment": "missingequipment",
        "missing_equipment": "missingequipment",
        "noise": "noise",
        "sick": "sick",
        "sickness": "sick",
        "success": "success",
        "unhappy": "unhappy",
    }

    mc_sounds = {}
    for role, family in sorted(ROLE_TO_JOB_FAMILY.items()):
        if role == "child":
            for child_voice, mapped_voice, mapped_profile in [
                ("male", "male3", 2),
                ("male1", "male3", 2),
                ("male2", "male2", 1),
                ("female", "female3", 2),
                ("female1", "female3", 2),
                ("female2", "female2", 1),
            ]:
                for raw_event, canonical_event in sorted(event_aliases.items()):
                    sound_list = get_filtered_event_clips(
                        role, None, mapped_voice, mapped_profile, canonical_event, event_sound_map, job_sound_map
                    )
                    event_key = f"citizen.child.{child_voice}.{raw_event}"
                    mc_sounds[event_key] = {
                        "replace": True,
                        "category": "neutral",
                        "sounds": [
                            {"name": sp, "stream": False, "weight": 3, "pitch": 1.15, "attenuation_distance": 28}
                            for sp in sound_list
                        ],
                    }
            continue

        for v_idx, voice_key in enumerate(voice_keys):
            profile_num = v_idx % 4
            for raw_event, canonical_event in sorted(event_aliases.items()):
                sound_list = get_filtered_event_clips(
                    role, family, voice_key, profile_num, canonical_event, event_sound_map, job_sound_map
                )
                if canonical_event in ("general", "success"):
                    for job_clip in get_valid_job_clips(role, family, voice_key, profile_num, job_sound_map):
                        if job_clip not in sound_list:
                            sound_list.append(job_clip)
                event_key = f"citizen.{role}.{voice_key}.{raw_event}"
                mc_sounds[event_key] = {
                    "replace": True,
                    "category": "neutral",
                    "sounds": [
                        {"name": sp, "stream": False, "weight": 3, "attenuation_distance": 28}
                        for sp in sound_list
                    ],
                }

    MC_SOUNDS_JSON.parent.mkdir(parents=True, exist_ok=True)
    MC_SOUNDS_JSON.write_text(json.dumps(mc_sounds, indent=2), encoding="utf-8")
    print(f"Updated {ADV_SOUNDS_JSON} ({len(adv_sounds)} keys) and {MC_SOUNDS_JSON} ({len(mc_sounds)} keys)")


if __name__ == "__main__":
    asyncio.run(main())

