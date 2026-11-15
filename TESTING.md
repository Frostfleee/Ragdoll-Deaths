# Death animation test checklist

Every living mob in Minecraft 26.3, grouped by the animation code it uses. Checked boxes are approved. Vanilla's death poof no longer appears where the mob stood: each body puffs vanilla's death smoke (plus a few silverfish specks) where it actually lay, when it disappears. Shulkers only break into their shulker box particles. Fixing one mob in a group usually fixes the whole group, so test the **first mob of each group carefully** and the rest quickly.

## Setup

1. Run `./gradlew runClient` and create a **Creative, Superflat** world with cheats on.
2. Useful commands:
   - `/tick freeze` freezes time mid-death, so you can fly around the body. `/tick step 1` advances one tick. `/tick unfreeze` resumes.
   - `/kill @e[type=!player]` gives a death with **no direction** (the "crumple" animation).
   - Babies: add `{IsBaby:1b}` to zombie-type mobs, or `{Age:-24000}` to animals.
3. Check `config/ragdolldeaths.json` has `"lifetimeTicks": 30`.

**Automatic screenshots.** `./gradlew runClientGameTest -PvisualTests` opens a fresh flat test world by itself, stages a hit up close, a TNT blast, a fall off a ledge, a burning zombie falling 40 blocks and a body in a flowing channel, then closes. Screenshots land in `build/run/clientGameTest/screenshots`. It's a quick first look before the manual checks below.

**Every loader and game version.** `./selftest-all.sh all` starts the game once per release jar group (Fabric, Quilt, NeoForge and Forge, 1.21 to 26.3) with `-Dragdolldeaths.selftest=true`. Each run creates a world, kills fifteen mobs (zombie, skeleton, creeper, spider, cow, shulker, slime, villager, enderman, piglin, horse, wolf, cat, pillager, vindicator) on a stone stage in the sky, carries one more body 90 blocks from where its mob died, kills the player, respawns, takes three screenshots, and prints one line: `PASS` when every mob became a physics body with nothing inside a block, the bodies were drawn, the dead player's body was there and followed by the death camera, it outlasted the body lifetime (Player Body Until Respawn), it was gone after the respawn, and the far-carried body was still drawn. With `EMF=1` in front, it runs with Entity Model Features and resource packs made for it (Fresh Animations, AL's packs) installed, then takes them out again. `EDGES=1` adds the oldest version of the groups built against a newer one; `./selftest.sh <loader> <-P...>` runs a single one (see `selftest-all.sh` for the values). Quilt runs start the game on the Quilt loader itself (`-PquiltRun`).

## Situations

| Code | How to cause it |
|---|---|
| **F** | Hit it from the **front** (it faces you). |
| **B** | Hit it from **behind**. |
| **S** | Hit it from the **side**. |
| **N** | No direction: `/kill`. |
| **Fall** | `/summon <mob> ~ ~30 ~` and let it fall. |
| **W** | Kill it while it's **in water**. |
| **L** | Kill it while it's **in lava**. |
| **Air** | Kill it while it's **flying** (flyers only). |

## 1. Two-legged mobs: test F, B, S, N, Fall, W, L

- [x] Zombie (baby too)
- [x] Husk
- [x] Drowned
- [x] Zombie Villager
- [x] Villager (crossed arms)
- [x] Wandering Trader
- [x] Witch
- [x] Pillager (holding crossbow)
- [x] Vindicator (holding axe)
- [x] Evoker
- [x] Illusioner (`/summon illusioner`)
- [x] Piglin (baby too)
- [x] Piglin Brute
- [x] Zombified Piglin
- [x] Enderman (long limbs)
- [x] Iron Golem (heavy)
- [x] Giant (`/summon giant`, very large)
- [x] Copper Golem
- [x] Frog (F, N, Fall): flips onto its back like the armadillo, arms and legs curled up
- [x] Player in third person (`playerRagdolls` is on by default)

## 2. Skeletons, bones scatter: test F, B, S, N, Fall (W and L use the two-legged animation)

- [x] Skeleton (with a helmet on: `/item replace entity @e[type=skeleton,limit=1] armor.head with iron_helmet`)
- [x] Stray
- [x] Wither Skeleton
- [x] Bogged
- [x] Parched

## 3. Special deaths

- [x] Warden (F, N, Fall): kneels, holds the kneel, falls face down with arms stretched forward past its head
- [x] Snow Golem (F, N, W): body melts, pumpkin falls off backward and keeps its shape
- [x] Armadillo (F, N, Fall): flips onto its back, curls up

## 4. Four-legged mobs: test F, B, S, N, Fall, W, L

When hit from the front or back, the body must never tilt nose-up or nose-down, and neither end may sink into the ground. Hit several of the same mob: some should roll left and some right.

In every direction, the body lies flat on its side with nothing under the ground: no head, rump or legs sinking in, no nose- or tail-first tilt. The legs on the ground side should tuck up a little, never point down into the ground. Also kill a sitting wolf, cat and camel, a sleeping fox, a grazing sheep and a rearing horse: each should drop that pose and fall from standing.

- [x] Cow (baby too)
- [x] Mooshroom
- [x] Pig
- [x] Sheep (sheared and unsheared)
- [x] Goat
- [x] Horse (also with a saddle and a rider)
- [x] Donkey (with a chest)
- [x] Mule
- [x] Skeleton Horse
- [x] Zombie Horse
- [x] Llama (with a carpet)
- [x] Trader Llama
- [x] Camel (very tall; also while sitting)
- [x] Camel Husk
- [x] Wolf (also while sitting)
- [x] Cat (also while sitting)
- [x] Ocelot
- [x] Fox (also while sleeping)
- [x] Panda
- [x] Polar Bear
- [x] Hoglin
- [x] Zoglin
- [x] Sniffer (six legs)

## 4b. Creeper and Ravager: test F, B, S, N, Fall, W, L

- [x] Creeper: legs buckle out only a little, body sags, then tips over away from the hit
- [x] Ravager: sinks onto its belly with legs splayed, jaw falls open; the head stays up and never goes into the ground; never rolls

## 5. Spiders: test F, S, N, Fall, W, L

- [x] Spider: flips onto its back, legs curl about 70% (still spread apart), only a faint twitch
- [x] Cave Spider

## 6. Winged mobs: test Air, plus N on the ground

- [x] Bat (wings must go almost still)
- [x] Phantom (wings must go almost still)
- [x] Parrot, flying and sitting (wings must go almost still)
- [x] Bee
- [x] Chicken: F, N, Fall (wings must go almost still)

## 6b. Deaths in mid-air

Two ways to cause it:
- **Knocked off:** stand a mob on a tall pillar (10+ blocks) and kill it so the hit knocks it off.
- **Already falling:** `/summon zombie ~ ~25 ~` and shoot it on the way down.

The body must keep falling with its limbs trailing up, play its death only after it touches the ground, then rest about 1.5 s. Over the void it vanishes after 3 s.

- [x] A two-legged mob (zombie), both ways
- [x] A skeleton (it should only fall apart after landing)
- [x] A four-legged mob (cow or horse)
- [x] A spider
- [x] A creeper
- [x] A slime
- [x] A fall into the void (vanishes after 3 s)
- [x] A short drop of 1 block or less (should die normally, no falling phase)

## 7. Floaters: test Air, plus N on the ground

- [x] Allay (falls and spins down like a bat, wings go still, lies where it lands)
- [x] Vex (falls and spins down like a bat, wings go still, lies where it lands)
- [x] Blaze: F, L, Air (rods slow to a stop, drop one by one and lie flat around it; the head falls, bounces and rolls over onto one face, away from the hit)
- [x] Breeze: F, L, Air (the whirlwind flares, then unwinds and shrinks into the ground; the head drops and lands upright, still facing ahead, no rolling; the three rods fall flat)

## 8. Ghasts: test Air from high up, and over lava in the Nether

- [x] Ghast: falls until it lands, tentacles stream up
- [x] Happy Ghast

## 9. Water mobs: test W, and on land (out of water)

- [x] Cod
- [x] Salmon
- [x] Tropical Fish
- [x] Pufferfish
- [x] Tadpole
- [x] Squid
- [x] Glow Squid
- [x] Dolphin
- [x] Guardian: W, on land (spikes shoot out, tail lashes without coming apart, rolls belly-up as the spikes sink in; on land flops twice and rolls onto its side, away from the hit)
- [x] Elder Guardian: W, on land (same as the guardian, slower and heavier)
- [x] Axolotl
- [x] Turtle (baby too)
- [x] Nautilus: W, on land, baby too (pulls its body into the shell; in water the shell turns its opening up and floats away; on land it rocks and tips onto its side)
- [x] Zombie Nautilus: W, on land (the rotten body slumps half out of the shell and hangs, then the shell behaves like the nautilus)
- [x] Surface timer: kill a fish or a zombie 3+ blocks deep. The body stays until it reaches the surface (or the bottom with Sink), up to 3 s

## 10. Cubes: test F, N, W, L, and splitting sizes

- [x] Slime, big, medium and small (stretch, slump into a puddle, jelly wobble)
- [x] Magma Cube (folds down in three steps)
- [x] Sulfur Cube (like the slime)

## 11. Odd shapes: test F, N

- [x] Shulker on a floor, a wall and a ceiling (vanishes instantly, doesn't fall, bursts into the same particles as breaking a shulker box; a dyed shulker uses its own color)
- [x] Silverfish
- [x] Endermite
- [x] Rabbit
- [x] Strider (also on lava)

## 12. Should stay vanilla (check nothing changed)

- [x] Ender Dragon
- [x] Wither
- [x] Creaking
- [x] Armor Stand
- [x] Mannequin

## 13. Settings screen (needs Mod Menu, already in the dev client)

- [x] *Mods → Ragdoll Deaths → Configure* opens, and every tooltip reads well
- [x] *Water Deaths* shows a single label (e.g. `Water Deaths: Float Up`)
- [x] *Animation Speed* at 50% and 200%
- [x] *Bounce* at 0%, and *Hit Push* at 0%
- [x] *Limb Animation* off: whole bodies still fall, limbs stay still
- [x] Typing in *Always Vanilla Death* (e.g. `zombie`) works after *Done*
- [x] *Reset to Defaults* restores everything
- [ ] *Thrown Riders* appears under Environment with a readable tooltip, and turning it off brings back the vanilla rider death
- [ ] There's no *Explosion Launch* toggle and no *May Keep Falling* list any more

## 14. New in 1.1.0: explosions

Use `/summon creeper ~ ~ ~5 {ExplosionRadius:3}` and wait for it to blow up near a group of mobs, or light TNT beside them. Try a mob right next to the blast and one about 4 blocks away.

- [ ] Zombie: thrown far away from the blast in a high arc (about 35 blocks right next to it, about 10 at the edge of the blast), falls over in the air (front or back, depending on which way it faced), lands with a bounce and slides to a stop
- [ ] The closer mob flies further than the farther one
- [ ] TNT (whose source is gone by the time it explodes) still throws bodies away from it, not toward it
- [ ] Cow or horse: thrown and lands on its side, nothing sinks into the ground
- [ ] Skeleton: thrown, then comes apart on landing
- [ ] A mob next to a wall is stopped by the wall, not pushed through it
- [ ] Indoors (under a low ceiling): the body hits the ceiling and drops to the floor, never stuck up against it
- [ ] A mob blasted off a ledge lands on the floor below and lies there
- [ ] Iron Golem or Ravager: thrown noticeably less far than a zombie at the same distance
- [ ] A body still in the air after 3 seconds keeps flying until it lands, then vanishes in its puff of smoke where it lay
- [ ] Creeper explosion in water: the water death plays (no launch)

## 15. New in 1.1.0: thrown riders

`/summon skeleton_horse ~ ~ ~ {Passengers:[{id:"minecraft:skeleton"}]}`, `/summon spider ~ ~ ~ {Passengers:[{id:"minecraft:skeleton"}]}`, `/summon chicken ~ ~ ~ {Passengers:[{id:"minecraft:zombie",IsBaby:1b}]}`, `/summon ravager ~ ~ ~ {Passengers:[{id:"minecraft:pillager"}]}`

- [ ] Skeleton on a skeleton horse: thrown off away from the hit, legs straighten out of the sitting pose, lands with the fall impact and falls apart
- [ ] Spider jockey, chicken jockey and ravager rider: the same, each landing beside its mount
- [ ] Kill the rider while its mount is walking or galloping: it is carried forward a little as it falls
- [ ] Player on a horse (third person): thrown off and lands
- [ ] The mount is not affected and keeps moving
- [ ] Rider in a boat or minecart: vanilla death, unchanged
- [ ] *Thrown Riders* off: riders keep the vanilla death

## 16. New in 1.1.0: uneven ground and culling

- [ ] A zombie killed at the bottom of a staircase, hit so it falls up the stairs: it rests tilted up along the steps, head on a higher step, nothing inside the blocks
- [ ] A zombie on a slab edge falling toward a lower block: it drapes down over the edge, at most a moderate angle
- [ ] A cow killed beside a 1-block step, falling onto its side against it: the body leans up on the step
- [ ] A mob killed on flat ground still lies perfectly flat (no tilt)
- [ ] A mob killed in mid-hop on flat ground lies flat, not tipped downward
- [ ] Ground changed during a fall: summon a zombie 20 blocks up, place blocks under it while it falls. It lands on the new blocks, not the old floor
- [ ] A body thrown or pushed to the edge of the screen no longer vanishes early while you look at it (turn the camera so the mob's original spot is just off screen)

## 17. New in 1.1.0: physics bodies

Run the automatic scenes first: `RAGDOLL_SCENES=<names> ./gradlew runClientGameTest -PvisualTests`. Each scene saves numbered screenshots and logs, for every body, whether it came to rest and whether any part ended inside a block (the test fails if one did). `RAGDOLL_DEBUG=1` adds how each blow was read and the physics time per tick. `RAGDOLL_KNEES=1` turns the knees beta on; `RAGDOLL_LIMP=1` turns dying muscles off.

| Scene | What must happen |
|---|---|
| `row1` `row2` | A chest hit knocks the body onto its back about 3 blocks away; a Knockback-strength hit about 7. |
| `row3` `row3b` | An arrow snaps the head back; a Punch-strength arrow throws the body about 8 blocks. |
| `row4` | Killed running: pitches forward and skids. |
| `row5` `row6` `row7` `row8` `row8b` | Off a pillar, against a wall, down stairs, against a fence, over a low wall: never inside a block. |
| `row9` `blast` `tnt` | Creeper and TNT throw bodies away; one behind an obsidian wall barely moves. |
| `row10` | Mace smash: driven down, no pop back up. |
| `row12` | Slides far on blue ice, bounces on slime, hangs in cobweb, sinks into powder snow. |
| `row13` | A second body lands on the first and stays on it. |
| `row14` | A hard-hit skeleton shatters; a plain-hit one falls whole; one dropped 8 blocks breaks on landing. |
| `row15` `row16` | A spider flips and curls; a chicken, parrot and bat shot in the air fall and land. |
| `row17` `specials` | Slime and magma cube tumble and keep their shape; snow golem pumpkin rolls off; blaze and breeze rods drop. |
| `plans1` `plans2` `plans3` | Mobs of many different shapes all fall and rest. |
| `pool` `lavapool` `columns` `water` `charred` | Floats in water and lava, drifts, rises fast over soul sand and slowly over magma, burns where lava touched. |
| `gear` | Armor and a held sword follow the body; a living mob beside it looks normal. |

Clips for the mod page: `RAGDOLL_SCENES=showcase RAGDOLL_CLIPS=<names>` films them at 50 frames a second of game time (names in `showcase()`). Two of them check 1.1.1: `shulker` (shulkers tumble as full boxes and break like shulker boxes when their time is up) and `death-camera` (you die and the view follows your body's head).

On 1.21.x run the same scenes with the `legacy/` build, for example `./gradlew -p legacy runClientGameTest -PvisualTests -Pminecraft_version=1.21.8 -Pfabric_api_version=0.136.1+1.21.8 -Pmodmenu_version=15.0.0 -Pcompat=mc1_21_5 "-Pminecraft_range=>=1.21.5 <1.21.9"` (the values for each group are in `build-all.sh`). Fabric has no client test API before 1.21.4: on 1.21.2 and 1.21.3, and on NeoForge and Forge everywhere, `runClient` with `-Dmixin.debug.verbose=true` (already set) lists every mixin applied as the game starts.

By hand, check what the scenes cannot: the sounds (thud, splash, bone clatter), your own death with Death Camera on, another player's death, and a crowd (64 zombies and TNT) for stutter.

## Reporting

One line per problem is enough:

```
Mob - situation - what looks wrong
e.g.  Llama - S - neck bends backward into the body
      Bee - Air - wings keep flapping after landing
      Zombie - F - looks good
```
