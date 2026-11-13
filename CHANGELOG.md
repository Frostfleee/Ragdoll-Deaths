# Changelog

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
