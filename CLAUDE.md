# Wintertodt QoL (RuneLite Plugin)

RuneLite's general plugin and Plugin Hub rules (from the example-plugin template) are in @AGENTS.md. Follow them too.

## What this is

A RuneLite plugin that restyles the Wintertodt minigame and adds a few cues. Built and tuned with the user in game on 2026-10-09 (with 117 HD):

1. **Darker, stormy atmosphere** in one arena color (the bright white snow is hard to look at for long).
2. **Snow attacks you can spot**: the falling snow is recolored and outlined.
3. **Custom colors**: the Wintertodt (main plus two accents), the fire, the door glow, and the supplies.
4. **Cues**: outlines on downed Pyromancers and broken or unlit braziers, a red screen-edge glow at low warmth, and a potion highlight.
5. **Missing supplies and points**: outlines the entrance crates for tools you lack (knife, hammer, axe, tinderbox or bruma torch), the potion crate if you have no potions, and the roots during a round if you have nothing to burn; and a points panel like RuneLite's built-in Wintertodt overlay (activity, round points toward 500, inventory points, kindling, next round timer).

Everything is client-side and cosmetic. Never automate input.

The snow attacks take the Wintertodt's colors (snow = accent 1, outline = accent 2); separate snow color pickers were removed at the user's choice and can be added back if wanted.

The user asked to keep it simple (2026-10-09): they removed the brightness, accent amount, fire glow, light beam, pool of light, ground mark and low warmth text settings. Prefer fixed, good defaults over new sliders.

## How it works

- Ids live in `WintertodtIds` (gameval constants). Arena region 6462, camp region 6461.
- Colors are the game's packed 16-bit HSL (`Hsl`): 6 bits hue, 3 saturation, 7 lightness. Values outside 0..0xFFFF are markers (-1 flat, -2 hidden) and are never touched.
- `ColorMemory` stores the original of every color array we change (weak, identity-keyed). Every recolor starts from the originals, so redoing it each frame or after a config change doesn't stack, and `restoreAll` undoes everything.
- **Map load (`SceneDarkener`, on `PreMapLoad`)**: darkens and tints the floor and scenery of the arena and camp with the arena color, fading out over `FADE_TILES` (32) tiles around them so there's no hard line against the plain white snow (floor corners each get their own strength; remap tables per strength level), and recolors objects that don't animate (Doors of Dinh glow, roots), because 117 HD uploads those once at load. Based on Dark Wintertodt (kulcris/darkwintertodt, MIT; notice in `THIRD_PARTY_NOTICES.md`). Settings that affect it reload the map with `client.setGameState(LOADING)`. It runs on the map loader thread, so it only touches renderables that are already plain `Model`s.
- **Every frame (`onClientTick`, client thread)**: recolors things that animate or move: burning braziers, the magic fire NPC (`WINT_FIRE`), projectiles, impact graphics, the falling snow objects, and the Wintertodt. Not in a `RenderCallback`: 117 HD draws on its own thread, and `getModel()` on an animated object must run on the client thread.
- **Fire**: flame-colored faces (red/orange/yellow and the green brazier flames) take the fire color, keeping lightness; the darkest 35% of each fire's flame faces (relative to the model) take the fire accent, and a brazier's faint glow layer takes the accent's hue softly (`Hsl.tintGlow`). Door glow and supplies use the main fire color. **Wintertodt**: main color, with accents on its darkest 40% of faces (relative to the model, since it's nearly all bright snow). **Snow**: near-white, so `Hsl.recolorWhite` pulls lightness into a mid range and fully saturates it, or the color wouldn't show.
- **Overlays**: `AtmosphereOverlay` (dim, dark edges, blowing snow over the 3D view; scaled by `atmosphereStrength` where the player stands, so it fades in and out with the scene), `CueOverlay` (snow outline, Pyromancer and brazier outlines with labels, low warmth edge glow), `PotionOverlay` (pulses rejuvenation potions when a full dose would not be wasted).
- **Supply items (`SupplyRecolor`)**: on `PostItemComposition`, the supply items' color swaps are extended so their colorful faces take the fire color, and the item definition, model and icon caches are reset when the color changes. The game draws the icons itself, so outlines, the see-through selected look and dragging work normally (an earlier overlay that painted over the icons broke those).
- `WintertodtProgress` counts roots, kindling and tools (by item name, so every axe/hammer variant and the bruma torch count) from the inventory and worn equipment, and tracks the activity from animations (the same list as the built-in plugin). Round points are read from the status panel's `WintStatus.POINTS` text. `PointsOverlay` shows them.
- Settings folders: About (top, collapsed by default), Gameplay help, Arena, Wintertodt, Fire, Fun (random colors) (RuneLite has no dividers or nested folders, and can only collapse whole folders). About holds a short note per folder: an abstract `void` `@ConfigItem` whose name is HTML, which the config panel shows as a text-only row (checked against RuneLite 1.13.1's `ConfigPanel`). The Fire note says 117 HD's glows can't be recolored. Keep the notes in sync when settings change. (A sidebar how-to page was tried; the user wanted the text inline.)
- **Random colors each round** (`randomColors`): `chooseColors()` sets the colors in use (`wintertodtColor`, accents, `fireColor`, `fireAccentColor`) from the config or a random pick; overlays read them from the plugin, not the config. A new pick happens when a round ends (game tick sees the storm go from howling to idle), and the map reloads then so the doors and picking roots (recolored at map load) match; a reload mid-round would get in the way. Turning it on also reloads.
- **Skiing** (`skiing`, Fun folder): `SkiPoses` swaps the local player's idle/walk/run pose animations for `HUMAN_SKI_*` (9341-9346) and, while not animating, shows the Skis item (`OSB9_REWARD_SKIS`, weapon slot, shield hidden) by editing the local `PlayerComposition` equipment ids and calling `setHash()`; while `atmosphereStrength > 0`, reapplied every client tick because the game resets poses on appearance changes; originals are kept and put back. Client-side only. Unverified that the skis item looks right.
- **Blowing snow** only falls while the storm is howling (`stormActive`). Each flake turns on or off only as it wraps back to the top, so snow eases in at round start and eases off at round end (up to ~10 s, the slowest fall).
- **Cold breath** (`coldBreath`, Fun): `BreathOverlay` puffs from every player's mouth (72% of logical height, 24 units ahead along `getCurrentOrientation`), kept in local coordinates and projected each frame; yours speed up as warmth drops.
- Warmth comes from varbit `WINT_WARMTH` (0-1000; confirmed 750 = 75%). A round counts as active when the storm object is the howling one (29308) or any brazier is lit.

## Findings (2026-10-09)

- **Snow attacks**: on players, projectile 1310 lands as graphic 1311. On braziers and Pyromancers, projectile 501 (aimed at the target's tile, path from the Wintertodt, `getZ()` returns garbage) lands as graphic 502. The snow you actually see falling is a separate animated object, 26690 (`CLANWARS_SNOWFALLING`), spawned on a plus shape of tiles around the target. Recoloring projectile 501 has no visible effect; recoloring object 26690 works.
- NPC 7383 "Snow" is only a flat 2-face ground marker.
- **117 HD** adds hard-coded green lights (165, 255, 56) to burning braziers and the Doors of Dinh (its `lights.json`); plugins can't change them.
- An earlier report that the Wintertodt recolor only worked with 117 HD was wrong: the (since removed) brightness setting was washing out the accents.

## Developer context

- The developer is strong in JavaScript/React, newer to Java. Keep code readable, well named, and commented where RuneLite APIs are non-obvious.
- When unsure about a RuneLite API, check the RuneLite source (sources jars are in the Gradle cache) rather than guessing. Flag anything you could not verify.
- RuneLite already has a core Wintertodt plugin. Don't duplicate what it does unless the user asks.

## Tech setup (local first)

- Same setup as the sibling plugins (`anger-management`, `alonescape`): Java 11 target, Gradle, package `com.wintertodtqol`, config group `wintertodtqol`.
- Local git repo, committed after each working step.
- GitHub: private repo https://github.com/calebleigh/osrs-plugins, one branch per plugin. Local `master` pushes to the `wintertodt-qol` branch (`git push` is preconfigured). Push after committing.
- Never close the user's running RuneLite to relaunch. Commit, say it's ready, and launch only after they close it.
- Test loop: `./gradlew run` (or run `WintertodtQolPluginTest` from IntelliJ) launches a RuneLite dev client with the plugin loaded.

### Publishing

- Author: DemiNerd (the user's RuneScape name). License BSD 2-Clause. `icon.png` (48x72) and `README.md` are at the root.
- The Plugin Hub builds from a public repo's root, so the plugin needs its own public repo. The hub entry is `plugins/wintertodt-qol` in runelite/plugin-hub: `repository=` + `commit=`. Updates are a new PR changing the commit hash. Don't open or update a hub PR until the user asks.
- The user's GitHub email is private, and past commits use their real email, so don't push them to a public repo without the user's OK.

## Testing checklist

Checked = confirmed in game.

Test in this order. Turn off the Dark Wintertodt plugin and RuneLite's Wintertodt plugin first if installed.

- [x] Plugin loads in developer mode with no errors
- [ ] Atmosphere: area darker and tinted with the arena color; darkness and color change it after a short reload
- [ ] Atmosphere: turning the plugin off brings the original colors back; other places untouched
- [ ] Storm overlay: dim, dark edges and blowing snow; nothing drawn over the interface
- [x] Wintertodt recolor: main color plus accents (confirmed 2026-10-09)
- [x] Falling snow recolored and brighter, easy to spot (confirmed 2026-10-09)
- [ ] Falling snow outline: color and strength settings
- [x] Magic fire sent at the Wintertodt takes the fire color
- [ ] Brazier flames in the fire color (the remaining green glow is 117 HD's light)
- [ ] Doors of Dinh glow in the fire color (partly; the rest is 117 HD's light)
- [ ] Roots (picking roots and wall roots) in the fire color, walls still darkened around them
- [x] Inventory supplies recolored by the game (outline, selected see-through look and dragging normal) (confirmed 2026-10-09)
- [ ] Potions highlight when a full dose would not be wasted (dose size setting, default 20%, unverified)
- [ ] Outlines and labels on downed Pyromancers and broken/unlit braziers
- [ ] Low warmth: red screen-edge glow at the threshold; glow strength setting
- [ ] Missing supplies: crates outlined for missing tools (try without a knife/axe, and with a bruma torch instead of a tinderbox), potion crate, roots when empty during a round
- [ ] Points panel: activity, round points (status panel text parse is unverified), inventory points, kindling, next round timer
- [x] Notes show as wrapped text with no input box (confirmed 2026-10-10)
- [ ] About folder at the top, collapsed by default, holds all the notes
- [ ] Random colors each round: new Wintertodt, snow, fire and supply colors when a round starts; own colors back when turned off
- [ ] Round start notification fires once, the set seconds before a round (0 = at the start), only while at the Wintertodt
- [ ] Storm fades out gradually around the camp (no hard line to plain white snow); overlay fades as you walk away
- [ ] Skiing: ski idle/walk/run poses around the Wintertodt, normal poses back when leaving or turning it off
- [ ] Blowing snow eases off when a round ends and eases back in when the next starts
- [ ] Cold breath: puffs come from the mouth (height and forward offset look right), faster when cold
