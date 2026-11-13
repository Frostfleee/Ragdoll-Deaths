# Changelog

## 1.1.1

### New
- **Minecraft 1.21 to 1.21.11.** Every loader now also runs on 1.21 to 1.21.11, with the same physics bodies (Forge from 1.21.3 for the 1.21.2-1.21.4 group, as there is no Forge for 1.21.2). Pick the jar for your version group: 1.21-1.21.1, 1.21.2-1.21.4, 1.21.5-1.21.8, 1.21.9-1.21.10 or 1.21.11. Forge splits two of them further, as its own event bus changed in 1.21.6: 1.21.3-1.21.4, 1.21.5 and 1.21.6-1.21.8.
- **Death camera follows your body.** While you are dead, the view follows your own body's head and slowly circles it, keeping out of walls, instead of staying third person where you died.
- **Player Body Until Respawn** (setting, on by default). A player's body stays where it fell until that player respawns, instead of going after the body lifetime.

  ![The death camera following your body, which stays until you respawn](https://cdn.modrinth.com/data/1eTrUOwY/images/7223d04675607fa8f82eefbea9fdbf4110129b65.gif)

- **Shulkers are physics bodies.** The killing blow knocks the shell over and it tumbles as the full box it is; when its time is up it breaks like a shulker box of its color, with the box's breaking sound.
- **New icon.** A zombie lying on open grass, with soft shadows.

### Fixed
- Your own body was thrown about three times too far by the killing blow: the knockback was counted twice.
- Your own body could vanish a moment after you died, while you were still on the death screen: a dead player is removed differently from a dying mob, and their body was not kept.
- The Quilt jars did not load on Quilt: they did not say they use Fabric's mappings, and Quilt read their Minecraft version range as a single bound.
- On Forge, a loading-error screen came up at startup and the mod's own resources (its settings text and icon) were left out: the jar had no pack.mcmeta.

## 1.1.0

Dead mobs are now real physics bodies instead of playing a fixed animation.

### New
- **Physics bodies.** A dying mob becomes a set of rigid parts joined at its joints. It falls, tumbles, slides and comes to rest on whatever blocks are there: stairs, slabs, fences, walls. It is built from the mob's own model, so armor and held items follow. Every mob gets one except the shulker (and the Ender Dragon, Wither and armor stands, which stay vanilla).
- **The killing blow matters.** A melee hit pushes the part it struck and knocks the body back; Knockback, sprint hits and Punch bows throw it further. Arrows hit the part they pass through. Explosions (creepers and TNT) fling every body away from the blast and are blocked by walls. A mace smash drives the body down.
- **Dying muscles.** For about a second and a half the body braces, reaches and curls, then goes limp. Arms go out in a forward fall; limbs twitch a little after landing.
- **Water and lava.** Bodies float instead of sinking, drift with the current, rise fast in a soul sand bubble column and slowly in a magma one. In lava they float higher, and the pixels that touched the lava are burnt black for good, with a darker ring around them.
- **Materials.** Bodies slide far on ice, bounce on slime, hang in cobwebs and sink part way into powder snow.
- **Special mobs.** Skeletons break apart at the joints when hit or landing hard. A snow golem's pumpkin (or its head, when sheared) rolls off. Blaze and breeze rods drop free. Spiders flip onto their backs and curl their legs; four-legged animals roll onto their side.
- **Sounds and dust.** Bodies thud on the block they land on, splash in water and clatter when they are loose bones. Only you hear them.
- **Settings.** A Physics section: Physics Bodies (off brings back the 1.0 animations), Knees and Elbows (beta, off by default), Physics Body Lifetime (5 seconds by default), Physics Quality, Max Physics Bodies, Explosion Throw, Dying Muscles, Fall Reflexes, Skeletons Break Apart, Impact Sounds, Impact Dust, Death Camera.

### Fixed
- On 26.1.x a render hook did not apply, so limb and armor posing could fail there.

### Known limits
- With dozens of bodies in the air at once the physics costs a few milliseconds per tick. It lightens itself and, if still far over, lets the oldest bodies go early.
- Lava burn marks do not show on armor or clothing layers, and a player's skin darkens as a whole.
- The death camera switches to third person where you died; it does not follow the body.
- A rider that dies on its mount still uses the 1.0 animation.
