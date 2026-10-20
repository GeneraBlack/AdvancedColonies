# 🏰 Advanced Colonies
**Next-Gen AI, Pathfinding, Worker Optimization, Context-Reactive Voicelines & 3D Visual Debugging for MineColonies (1.21.1 NeoForge)**

**Advanced Colonies** is a ground-up AI, logistics, and immersion expansion for **MineColonies**. Designed with clean, non-destructive Mixin hooks and strict **server-friendly configuration limits**, it eliminates classic pathfinding bottlenecks, stops guards from clumping into a single "deathball", supercharges worker productivity, and brings your colonists to life with **context-reactive 3D speech bubbles and emotional voice acting**.

---

## 🌟 Highlight Features

### 🧭 Smarter, Faster & Safer Pathfinding
* **Zero-Oscillation A\* Heuristics**: Uses a Manhattan tie-breaker heuristic and zero-allocation node checks to prevent citizens from stuttering or looping on complex paths.
* **Road & Footpath Speed Boost**: Citizens actively favor colony roads and receive a configurable movement speed bonus while walking on path blocks.
* **Safe Vertical Rails, Ladders, Vines & Scaffolding**: Fixes mid-air dismounts and fall damage when workers climb vertical rails, vines, or scaffolding.
* **Self-Healing Anti-Stuck Recovery**: Automatically detects stuck citizens, cancels stale background path jobs, prevents walking into unloaded chunks, and safely recovers trapped workers without suffocating them in walls.

---

### 🗣️ Context-Reactive Citizen Voicelines & 3D Speech Bubbles
Tired of colonists complaining about the rain while standing indoors, or complaining about their house while working on the other side of town?
* **21 Live Situational Contexts**: Citizens evaluate their actual surroundings before speaking—reacting accurately to **active raids, guard combat vs. civilian panic, tactical retreats, illness, graveyard mourning, true outdoor rain/thunderstorms, hunger, restaurant dining, missing tools, home comfort, morning commutes,** and **passing guards or neighbors**.
* **380+ Natural English Lines Across 28 Professions**: Every MineColonies job (*Builder, Farmer, Miner, Quarrier, Lumberjack, Courier, Cook, Baker, Healer, Researcher, Enchanter, Concrete Mixer, Blacksmith, Smelter, Carpenter, Stonemason, Herders, Beekeeper, Fisherman, Alchemist, Undertaker, Knight, Ranger, Druid*, and more) features authentic, craft-specific lines.
* **376 Built-In Emotional Voice Clips**: Ships with 376 mono 3D-positional `.ogg` voice lines across all **8 citizen voice profiles** (`male1`–`male4`, `female1`–`female4`) that act out genuine panic during raids, exhaustion at bedtime, shivering in storms, and pride after finishing a job.
* **Full TownTalk Compatibility**: Seamlessly routes *TownTalk* sound events to the right situation and expands the audio pool across 10,150 citizen sound events.
* **Floating 3D Speech Bubbles**: Renders clean, color-coded 3D speech bubbles (`[Name • Job]`) above speaking citizens.

---

### 🛡️ Tactical Guard Patrol Dispersion & Swarm Defense
* **Peacetime Patrol Dispersion**: Stops guards from walking through your colony in one big cluster. Guards dynamically spread out across unvisited buildings and sectors, maximizing **security coverage** and **citizen happiness**.
* **Automatic Raid & Mob-Swarm Convergence**: As soon as a raid starts or a large group of hostile mobs (`3+` enemies) is detected, guards automatically switch from dispersed patrol to **coordinated swarm formation** to eliminate the threat together.
* **Fleeing Guard Protection**: Rebalances raider threat targeting so critically wounded, retreating guards aren't endlessly chased down while healthy guards are engaging the enemy.

---

### 🔍 In-Game 3D Visual AI & Pathfinding Debugger (`/acdebug`)
See exactly what your citizens are thinking, building, and where they are walking—right in the game world:
* **Floating HUD Billboards & 2D Top-Right Inspector**: Shows `[#ID] Name (Job • Building Lv.X)`, real-time Citizen & Worker AI states (`AI: WORK -> BUILD_SOLID`), active Builder stage & work order (`Build: CLEAR_STEP [Builder's Hut] @ (x, y, z)`), open material requests/illness alerts, vitals & inventory fill (`HP | Sat | Hap | Hand | Inv`), live speed (`b/s`), and genuine 3D stall diagnostics (`⚠ STUCK [Lv.2 | Stalled 6.5s]`).
* **3D World-Space Path & Builder Target Visualization**: Renders color-coded 3D boxes and route lines for upcoming waypoints (Green), active node (Yellow), reached nodes (Blue), ladders/vines (Cyan), rails (Orange), Builder target blocks (Orange-Gold), Builder `workFrom` stands (Cyan-Lime), and stuck target vectors (Red).
* **Easy Chat Commands (with Clickable `[Focus]`, `[Inspect]`, `[TP]`, `[Unstuck]` Chat Buttons)**:
  * `/acdebug look` — Toggle debug overlay for the citizen you are looking at (or Shift + Right-Click with a Compass)
  * `/acdebug colony` — Toggle debug overlays for your entire colony
  * `/acdebug job <jobFilter>` — Filter colony debugger to a specific profession (e.g. `/acdebug job builder`)
  * `/acdebug stuck` — Filter to genuinely stalled citizens and list them with clickable `[Unstuck]` / `[TP]` buttons
  * `/acdebug unstuck [<id>|look|all]` — Immediately execute multi-stage unstuck teleport & reset stuck Builder AI
  * `/acdebug inspect [<id>|look]` — Print a complete diagnostic report for a citizen in chat
  * `/acdebug tp <id>` — Teleport directly to a citizen
  * `/acdebug list [<filter>]` — List loaded colony citizens with status and clickable action buttons
  * `/acdebug layer <actions|stats|path|all>` — Customize visible debug layers
  * `/acdebug off` — Turn off all debug overlays

---

### ⚒️ Worker & Building Upgrades (100% Configurable & Server-Capped)
* **Builder**: Batch block placement & scavenging, instant block breaking, fluidlogged block cleanup, optional top-down/bottom-up build order reversal, and relaxed hut-level upgrade limits.
* **Miner & Quarrier**: Instant Cobblestone sealing for underground water, lava, and air pockets to prevent flooded shafts.
* **Farmer & Planter**: Fixed outward spiral field scanning, crop survival fixes, right-click harvest scaling, and **Multi-Season Crop Rotation** per Farm Field.
* **Lumberjack**: Fixed downward stump detection for floating **Mangrove Propagules** and faster timber harvesting.
* **Deliveryman & Warehouse**: Street-grouped batch deliveries/pickups and multi-warehouse courier assignment.
* **Smelter, Cook & Kitchen**: Skill- and building-level-scaled furnace smelting speed and fuel efficiency, **non-blocking Out-of-Order Dining Hall customer pool** (drive-by serving & automatic stuck-customer bypass so one unreachable citizen never blocks the restaurant), and instant citizen dining.
* **Enchanter**: Batch Ancient Tome/Book requests, building-level mana caps, and autonomous daily XP collection.
* **Concrete Mixer**: High-speed batch powder placement and cured concrete harvesting.
* **Cowboy, Beekeeper & Enhanced Bee AI**: Multi-milking/stewing per visit, no `Large Bottle` cooldown, Beekeeper hive-harvest priority, and a complete **Minecraft Bee AI Overhaul** (persistent hive memory & 16-block home tether so bees never drift away or vanish, hive-entry deadzone & 3D roof/fence unstuck fixes, 16-block flower scanning & fast 8s pollination without vanilla's 2-minute delay, and protection against campfires, water drowning, berry bushes, and colonist sting deaths).
* **GUI & Colony QoL**: Multi-keyword item search (with *Just Enough Characters* support), Tavern coin recruitment, remote Hospital curing, farmland trample protection, configurable grave decay/mourning timers, and full-colony Beacon range extension.

---

## ⚙️ Server-Friendly Configuration
Every feature, multiplier, batch size, and cooldown can be toggled or capped in `config/advancedcolonies-common.toml` (`[pathfinding]`, `[citizens]`, `[citizens.debug]`, `[citizens.voicelines]`, `[building_capacity]`, `[logistics]`, `[furnace_and_craft]`, `[builder_and_excavation]`, `[agriculture_and_herding]`). Server admins can also define custom voiceline templates with dynamic placeholders (`{citizen}`, `{job}`, `{building}`, `{building_level}`, `{colony}`, `{player}`, `{guard}`, `{friend}`, `{weather}`, `{time}`).

---

## 📦 Requirements & Installation
* **Minecraft**: `1.21.1`
* **Mod Loader**: `NeoForge 21.1.200+`
* **Required Dependency**: **MineColonies** (`1.1.1285-1.21.1` or newer) & its standard dependencies (*Structurize*, *BlockUI*, *Multi-Piston*, *Domum Ornamentum*)
* **Side**: Required on both **Client & Server**
