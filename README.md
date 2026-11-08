<p align="center"><img src="src/main/resources/assets/ragdolldeaths/icon.png" width="128" alt="Ragdoll Deaths icon"></p>

<h1 align="center">Ragdoll Deaths</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.1--26.3-62b47a" alt="Minecraft 26.1-26.3">
  <img src="https://img.shields.io/badge/loaders-Fabric%20%7C%20Quilt%20%7C%20NeoForge%20%7C%20Forge-4a7bbf" alt="Fabric, Quilt, NeoForge, Forge">
  <img src="https://img.shields.io/badge/side-client-8a8a8a" alt="Client-side">
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="MIT license">
</p>

A small, client-side visual mod. When a mob dies, its body collapses with jointed limbs, rests on the ground for a moment, and disappears about 1.5 seconds later in a puff of smoke where it lay. Nothing changes gameplay.

## Requirements

| | |
|---|---|
| Minecraft | Java Edition **26.1 to 26.3** |
| Loader | **Fabric** 0.18.0+, **Quilt** 0.30.1+, **NeoForge** or **Forge** |
| Required mod | **Fabric API** on Fabric and Quilt; nothing on NeoForge or Forge |
| Optional | **Mod Menu** on Fabric and Quilt, for the in-game settings screen |
| Java | 25 |

## Installation

Pick the jar for your loader and game version, for example `Ragdoll Deaths v1.0.1 - NeoForge 26.2-26.3.jar`, and put it in your `mods` folder. On Fabric and Quilt, add Fabric API too, and Mod Menu if you want the settings screen. NeoForge and Forge have the settings screen built in, under *Mods → Ragdoll Deaths → Config*.

The mod is **client-only**. Servers don't need it, and every loader skips it on a dedicated server, so no rendering code ever loads server-side. In multiplayer, each player who installs it sees the ragdolls on their own screen.

## What it does

Each death is a short, hand-tuned animation with joints, not a stiff tip-over. The mod reads the model's own named parts (`head`, `right_arm`, `left_hind_leg`, `tentacle3`, …), so the limbs of vanilla mobs, and of most modded mobs, move on their own. Armor and held items follow the limbs they're attached to.

The animation is picked from data the client already receives: the final damage source, the surroundings and the body plan.

**Two-legged mobs** (zombies, skeletons, villagers, illagers, golems, players):

| Death | Animation |
|---|---|
| Hit from the front | Head snaps back and the arms reach up while the body falls backward. One leg kicks. Just after the back hits the ground, the arms splay out and the head rolls aside. |
| Hit from behind | The body arches, then the arms are thrown forward to break the fall. It lands face down with the head turned. |
| Hit from the side | The body twists away from the blow. One arm is flung across the body, the other swings wide, and the legs scissor. |
| No direction (poison, magic, starving) | The knees give out into a kneel, the head droops, then the body folds forward onto its face. |
| Fall damage | The knees buckle on impact and the body slams flat, spread-eagled. |
| Water | Rolls slowly onto its back with arms drifting up and out. |
| Lava | Arms flail overhead as it tips back and sinks. |

**Four-legged mobs** (livestock, horses, donkeys, llamas, camels, wolves, cats, bears, hoglins, sniffers):

Whatever pose the mob died in (a horse rearing from the hit, a sitting wolf or cat, a sleeping fox, a grazing sheep, a sitting camel) is cleared, so it falls from a normal standing pose. The body keeps the heading it died with instead of turning while it falls. Their legs have no knees, so they stay nearly straight; the body does the falling, and it never pitches forward or back, so neither end sinks into the ground. Front and back hits roll the body to a side that varies per mob. Large animals rest on their real torso width, not their wider collision box, so they lie flat on the ground.

| Death | Animation |
|---|---|
| Hit from the side or no direction | Sways away from the blow, the legs give a little, and it rolls over onto its side. Once down, the legs stretch out stiffly with a couple of fading kicks and the head settles to the ground. |
| Hit from the front | Braces its front legs and tosses its head as it staggers back a step, then rolls onto its side. |
| Hit from behind | Kicks its hind legs out and dips its head as it stumbles forward, then rolls onto its side. |
| Fall damage | Belly flop with the legs splayed out sideways. |
| Water | Rolls onto its side, paddling weaker and weaker. |
| Lava | Thrashes while sinking. |

**Other body plans:**

| Mob | Animation |
|---|---|
| Spiders and other angled-leg crawlers | Legs flare, the body flips onto its back, and the legs curl about 70% of the way in with a faint twitch. |
| Bats, phantoms, parrots, chickens | The wings stop flapping, settle to rest and only quiver faintly, and the feet tuck. Flyers keep momentum and spiral down. |
| Bees | A short burst of weakening wing buzz, then the wings droop. |
| Allays, vexes | Fall like bats: the wings go still and the body spirals down and lies where it lands. |
| Blazes | The fire goes out: the circling rods slow to a stop and drop one by one, tipping over flat and rolling out around the body, while the head falls, bounces and rolls over onto one face, away from the blow. |
| Breezes | The whirlwind gives out: it flares once, then unwinds and shrinks into the ground. The head drops and bounces, landing upright and still facing ahead, and the three rods clatter down flat around it. |
| Ghasts and happy ghasts | Drop with gravity, tentacles streaming upward, then splay flat on landing. They keep falling after vanilla removal, until they land (3 s at most). |
| Fish, squid, dolphins | Fins flop out of water and slow to a stop in it. Squid tentacles curl in. In water they roll belly-up and float. |
| Guardians and elder guardians | In water: convulses with its spikes shot out and its tail lashing, then slowly rolls belly-up as the spikes sink back in and the tail goes still. On land: flops twice, then rolls onto its side. The elder is slower and heavier. |
| Nautiluses | The body pulls back into the shell. In water the shell turns its opening up and floats away; on land it rocks and tips onto its side. A zombie nautilus's rotten body slumps half out of the shell and hangs instead. |
| Slimes and sulfur cubes | The hit stretches the body tall as it leans away, then it slumps into a wide puddle and wobbles like jelly before settling. Splitting is unchanged. |
| Magma cubes | Stiffer: they fold down in three stacked steps, like a collapsing accordion, with a small clack at each step. |
| Shulkers | No gravity and no animation: the shulker disappears the moment it dies and breaks apart into the same particles, in the same spots and number, as breaking a shulker box of its color (purple for an undyed shulker). |
| Anything else | The head droops, arms hang, and tails, ears and jaws go slack. |

**Special deaths:**

| Mob | Animation |
|---|---|
| Skeletons, strays, wither skeletons, bogged | The legs give out and the skeleton drops onto its pelvis, then comes apart. The skull pops off and rolls away from the hit, the arms and legs clatter flat, and the ribcage lands in the pile. Armor and held items stay with their bones. |
| Snow golem | The snow body melts into a flat patch. The pumpkin keeps its shape, rides down on top, then tips off backward. |
| Warden | Staggers and sinks to its knees, stays kneeling a moment with its head hanging, then slowly tips forward and lies face down with its arms stretched out past its head. Its body stays an extra second. |
| Creeper | The four stubby legs buckle out a little and the tall body sags onto them, then it tips over away from the blow and lies flat with its head rolled aside. A fall does the same, faster. |
| Ravager | Reels from the blow, its legs splay out and it sinks heavily onto its belly, then its jaw falls open. The head keeps its height, and it stays upright on its belly and never rolls over. |
| Armadillo and frog | Flips onto its back with its head, tail and limbs curled up. |

In lava the special mobs use their normal body-plan death.

**Deaths in mid-air.** Any mob that dies more than about 1.5 blocks above the ground (knocked off a ledge, shot while jumping from a height) keeps falling under gravity with its limbs trailing upward. This includes a mob that dies standing on a ledge and is knocked off by the killing blow. Its death animation, the fall-impact version, only starts once the body actually lands. A body still in the air when vanilla removes the mob keeps falling to the floor and stays until it lands. If it hasn't landed after 3 seconds (`ghostMaxTicks`), for example over the void, it disappears in the air. Flying mobs and ghasts already fall this way.

**Deaths in deep water.** A body that floats up (or sinks, with `waterBehavior` set to `SINK`) speeds up as it goes and stays until it reaches the surface or the bottom, plus half a second. If it hasn't got there after 3 seconds (`ghostMaxTicks`), it disappears where it is.

Heavy mobs move slower and with less overshoot. Babies are a little quicker. While the ragdoll is shown, the red death tint is hidden; normal hits still flash red exactly as in vanilla. Vanilla's death poof is moved: instead of puffing where the mob stood when vanilla removes it, the body puffs where it actually lay when it disappears, with vanilla's death smoke plus a few silverfish (infested) specks. Shulkers only break into their shulker box particles.

### Kept vanilla on purpose

These keep the plain vanilla death: the **Ender Dragon** and **Wither** (scripted boss deaths), **armor stands** and **mannequins** (they break rather than die), and the **Creaking**.

**Players** get ragdolls by default; turn `playerRagdolls` off to give them the vanilla death.

## Configuration

On NeoForge and Forge, or on Fabric and Quilt with **Mod Menu** installed, open *Mods → Ragdoll Deaths → Config* for a settings screen with tooltips and a *Reset to Defaults* button. Changes apply as soon as you press *Done*. Otherwise, edit `config/ragdolldeaths.json`; it's re-read every time you join a world. Invalid values are clamped, and if the file is unreadable the mod logs a warning and uses the defaults.

**General**

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Turns the death animations on or off. When off, every mob and player dies with the normal Minecraft animation. |
| `playerRagdolls` | `true` | Animate players, yourself included in third person. |
| `lifetimeTicks` | `30` | How long a body stays (4–60 ticks). Past vanilla's 20 ticks the body is drawn client-side after the server removes the mob. |
| `animationSpeed` | `1.0` | Plays every death faster or slower (0.5–2). |
| `maxRenderDistance` | `64.0` | Deaths farther from the camera use the vanilla death. |
| `maxActiveRagdolls` | `64` | Cap on bodies animating at once. |
| `hideDeathTint` | `true` | Hide the red tint on dying bodies. |

**Motion**

| Key | Default | Meaning |
|---|---|---|
| `hitReactionStrength` | `1.0` | Push from the killing blow (0–2). At `1.0` the body moves about 0.35 blocks. |
| `bounceStrength` | `1.0` | Bounce and settle on landing (0–2). |
| `limbAnimation` | `true` | Animate heads, arms, legs, wings and tails. Off moves the whole body only. |
| `legKicks` | `true` | Fading kicks, twitches and paddling after landing. |

**Environment**

| Key | Default | Meaning |
|---|---|---|
| `waterBehavior` | `FLOAT` | `FLOAT`, `SINK` or `NEUTRAL`, for land mobs dying in water. A floating or sinking body speeds up as it goes and stays until it reaches the surface or the bottom, up to `ghostMaxTicks`. Water mobs always float. |
| `lavaSink` | `true` | Sink into lava. |
| `fallImpact` | `true` | Slam flat on fall-damage deaths. |

**File only.** `wardenExtraTicks` (default `20`, 0–60) is the Warden's extra time, and `ghostMaxTicks` (default `60`, 20–100) is the longest a body may keep falling to the ground, or floating up to the surface or sinking to the bottom in water. These two aren't on the settings screen.

**Lists**

| Key | Default | Meaning |
|---|---|---|
| `excludedEntities` | `[]` | Entity ids that always keep the vanilla death, e.g. `"somemod:big_worm"`. A bare name means `minecraft:`. |
| `ghostEntities` | ghast, happy ghast | Flying entities whose body may keep falling after removal until it lands. |

## Compatibility philosophy

- **Visual only.** Deaths, drops, XP, sounds, advancements, scoreboards, game rules and AI are untouched. Nothing is sent over the network, and no entities are created or replaced. The body that lingers after vanilla removal is only drawn on your client.
- **No ticking physics.** Each pose is computed from a small profile captured once at death, plus elapsed time. Raycasts against walls, the floor and the water surface run once per death, not every frame.
- **Narrow hooks.** A few small mixins:
  - after an entity's render state is built,
  - one transform after the living renderer's first `pushPose`,
  - after a model's own `setupAnim`, to rotate joints only for a body that is mid-death,
  - replacing vanilla's 90° death-flip angle only while a ragdoll is active,
  - skipping the vanilla death poof, only for bodies this mod is animating (the mod spawns its own where the body lay).
- **Graceful fallback.** Mobs whose renderer isn't a standard living renderer (for example GeckoLib models) simply keep the vanilla death. If a renderer throws, ragdolls are turned off for that one entity type for the session, with a single log line, and vanilla takes over.

## Limitations

- Joints are recognised by part names. Modded models with unusual names fall back to a whole-body animation with a limp head.
- Limbs don't collide with terrain; poses are authored to rest on flat ground.
- Collision is approximate. The body is kept out of walls and above the floor using checks made at the moment of death, so terrain that changes during the fall isn't noticed.
- A ragdoll is only built if the mob was rendered while dying. Deaths you can't see stay vanilla.
- Rendering of a pushed or falling body is culled by the mob's real hitbox. A body pushed far outside the view can pop out a frame early.
- Modded mobs with fully custom renderers keep vanilla deaths. Add `excludedEntities` entries for any mob that looks wrong.
- Edits to the config file apply on the next world join; the settings screen applies them immediately.

## Troubleshooting

- **A mod's mob looks wrong:** add its id to `excludedEntities`.
- **"Disabling ragdolls for … after an error" in the log:** that mob's renderer couldn't handle the transform. It now uses the vanilla death for the rest of the session. Please report it with the log.
- **Nothing happens:** check that Fabric API is installed (Fabric and Quilt), `enabled` is `true`, and the mob isn't farther away than `maxRenderDistance`.
- **Want the vanilla death for one mob:** add its id to *Always Vanilla Death* (`excludedEntities`).

## Building from source

```bash
./build-all.sh
```

This builds every jar, one per loader and game-version group, into `dist/<Loader>/` (pass a folder to use another location). `./gradlew build` alone makes the Fabric jar for 26.2–26.3 in `build/libs/`, `./gradlew test` runs the unit tests, and `./gradlew runClient` starts a development client.

How the sources are split:

- `src/main`: everything that doesn't depend on the loader or the game version.
- `src/mc26_1` and `src/mc26_2`: the few calls that changed between 26.1 and 26.2 (`Compat` and one mixin).
- `src/fabric`, `src/quilt`, `src/neoforge`, `src/forge`: each loader's entrypoint and metadata. `src/forgelike_*` holds the ghost-body mixin that NeoForge and Forge use in place of Fabric API's extraction event.
- The root Gradle build makes the Fabric and Quilt jars; `neoforge/` and `forge/` are separate Gradle builds over the same sources.
