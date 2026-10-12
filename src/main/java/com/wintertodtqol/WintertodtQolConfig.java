package com.wintertodtqol;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(WintertodtQolConfig.GROUP)
public interface WintertodtQolConfig extends Config
{
	String GROUP = "wintertodtqol";

	// The About notes are HTML labels, wrapped to the settings panel width
	String NOTE_START = "<html><body style='width:165px; color:#a5a5a5'>";
	String NOTE_END = "</body></html>";

	// ---------------------------------------------------------------------------------------
	// About: notes on what the settings do. Collapsed until opened.

	@ConfigSection(
		name = "About",
		description = "What the settings do, with tips and the 117 HD limits",
		position = 0,
		closedByDefault = true
	)
	String aboutSection = "about";

	// Text only: an abstract item with no value shows just its name
	@ConfigItem(
		keyName = "arenaNote",
		name = NOTE_START + "<b style='color:white'>Arena</b><br>Darkens and tints the area toward the arena color. The scenery is recolored as the map loads, so changing these reloads the area for a moment." + NOTE_END,
		description = "",
		section = aboutSection,
		position = 0
	)
	void arenaNote();

	// Text only: an abstract item with no value shows just its name
	@ConfigItem(
		keyName = "wintertodtNote",
		name = NOTE_START + "<b style='color:white'>Wintertodt</b><br>The snow attacks come from the Wintertodt, so they share its colors: the falling snow is accent 1 and its outline is accent 2. Snow falling onto a brazier means it will go out or break; onto a Pyromancer, that they will take damage." + NOTE_END,
		description = "",
		section = aboutSection,
		position = 1
	)
	void wintertodtNote();

	// Text only: an abstract item with no value shows just its name
	@ConfigItem(
		keyName = "fireNote",
		name = NOTE_START + "<b style='color:white'>Fire</b><br>The fire color is used for the flames, the magic fire, the Doors of Dinh, the roots, your supplies and the highlights.<br><br><b>With 117 HD, the glows won't change properly.</b> 117 HD adds its own green light around burning braziers and the doors, which plugins can't recolor. Turning down 117 HD's Dynamic lights may hide it, but that's untested and affects every light in the game." + NOTE_END,
		description = "",
		section = aboutSection,
		position = 2
	)
	void fireNote();

	// Text only: an abstract item with no value shows just its name
	@ConfigItem(
		keyName = "helpNote",
		name = NOTE_START + "<b style='color:white'>Gameplay help</b><br>Highlights use your fire color. If RuneLite's built-in Wintertodt plugin is on, turn off its overlay and set its round notification to 0, so you don't get two points panels or two notifications.<br><br><b style='color:white'>Random colors</b><br>Random colors each round (in the Fun folder) picks new colors for the Wintertodt, the fire, the doors, the roots and the supplies after every round. The area reloads for a moment when they change." + NOTE_END,
		description = "",
		section = aboutSection,
		position = 3
	)
	void helpNote();

	// ---------------------------------------------------------------------------------------
	// Arena: the darker, stormy look of the area

	@ConfigSection(
		name = "Arena",
		description = "The darker, stormy look of the area",
		position = 2
	)
	String arenaSection = "arena";

	@ConfigItem(
		keyName = "arenaColor",
		name = "Arena color",
		description = "Color the snow, scenery and storm are tinted toward",
		section = arenaSection,
		position = 0
	)
	default Color arenaColor()
	{
		return new Color(70, 90, 140);
	}

	@ConfigItem(
		keyName = "darkenScene",
		name = "Darken and tint the area",
		description = "Darken the floor, walls and scenery and tint them with the arena color",
		section = arenaSection,
		position = 1
	)
	default boolean darkenScene()
	{
		return true;
	}

	@Range(max = 90)
	@ConfigItem(
		keyName = "darkness",
		name = "Darkness",
		description = "How much darker the area gets, in percent",
		section = arenaSection,
		position = 2
	)
	default int darkness()
	{
		return 45;
	}

	@ConfigItem(
		keyName = "stormOverlay",
		name = "Storm overlay",
		description = "Draw a storm over the game view: dimming, dark edges and blowing snow",
		section = arenaSection,
		position = 3
	)
	default boolean stormOverlay()
	{
		return true;
	}

	@Range(max = 60)
	@ConfigItem(
		keyName = "blowingSnow",
		name = "Blowing snow",
		description = "Snowflakes blowing across the screen while a round is on, in tens (15 = 150 flakes). 0 turns it off.",
		section = arenaSection,
		position = 4
	)
	default int blowingSnow()
	{
		return 15;
	}

	// ---------------------------------------------------------------------------------------
	// Wintertodt: its colors and its snow attacks

	@ConfigSection(
		name = "Wintertodt",
		description = "The Wintertodt's colors and its snow attacks",
		position = 3
	)
	String wintertodtSection = "wintertodt";

	@ConfigItem(
		keyName = "tintWintertodt",
		name = "Recolor the Wintertodt",
		description = "Recolor the Wintertodt with a main color and two accents",
		section = wintertodtSection,
		position = 0
	)
	default boolean tintWintertodt()
	{
		return false;
	}

	@ConfigItem(
		keyName = "wintertodtColor",
		name = "Main color",
		description = "Main color of the Wintertodt",
		section = wintertodtSection,
		position = 1
	)
	default Color wintertodtColor()
	{
		return new Color(225, 235, 255);
	}

	@ConfigItem(
		keyName = "wintertodtAccent1",
		name = "Accent 1",
		description = "Accent color on the Wintertodt's darker parts, and the color of its snow attacks",
		section = wintertodtSection,
		position = 2
	)
	default Color wintertodtAccent1()
	{
		return new Color(90, 200, 255);
	}

	@ConfigItem(
		keyName = "wintertodtAccent2",
		name = "Accent 2",
		description = "Accent color on the Wintertodt's darkest parts, and the outline of its falling snow",
		section = wintertodtSection,
		position = 3
	)
	default Color wintertodtAccent2()
	{
		return new Color(155, 90, 255);
	}

	@ConfigItem(
		keyName = "recolorSnowfall",
		name = "Recolor snow attacks",
		description = "Recolor the snow falling onto braziers and Pyromancers, and its impact",
		section = wintertodtSection,
		position = 4
	)
	default boolean recolorSnowfall()
	{
		return true;
	}

	@ConfigItem(
		keyName = "glowSnowfall",
		name = "Outline falling snow",
		description = "Outline the snow falling onto a brazier or Pyromancer",
		section = wintertodtSection,
		position = 5
	)
	default boolean glowSnowfall()
	{
		return true;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "snowOutlineStrength",
		name = "Outline strength",
		description = "How solid and wide the falling snow outline is",
		section = wintertodtSection,
		position = 6
	)
	default int snowOutlineStrength()
	{
		return 50;
	}

	// ---------------------------------------------------------------------------------------
	// Fire: the fire, the braziers and the supplies you burn

	@ConfigSection(
		name = "Fire",
		description = "The fire, the braziers and the supplies you burn",
		position = 4
	)
	String fireSection = "fire";

	@ConfigItem(
		keyName = "fireColor",
		name = "Fire color",
		description = "Color of the brazier flames, the magic fire, the door glow, the supplies and the cues",
		section = fireSection,
		position = 0
	)
	default Color fireColor()
	{
		return new Color(155, 90, 255);
	}

	@ConfigItem(
		keyName = "fireAccent",
		name = "Fire accent",
		description = "Accent color on the deepest parts of the flames and the glow in the brazier",
		section = fireSection,
		position = 1
	)
	default Color fireAccent()
	{
		return new Color(95, 45, 210);
	}

	@ConfigItem(
		keyName = "recolorFire",
		name = "Recolor the fire",
		description = "Recolor brazier flames, the magic fire sent at the Wintertodt and the glow of the Doors of Dinh. With 117 HD, its own green glow around braziers and the doors can't be recolored.",
		section = fireSection,
		position = 2
	)
	default boolean recolorFire()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recolorSupplies",
		name = "Recolor supplies",
		description = "Recolor bruma roots, kindling, herbs and finished rejuvenation potions, in the inventory and on the roots you pick from",
		section = fireSection,
		position = 3
	)
	default boolean recolorSupplies()
	{
		return true;
	}

	// ---------------------------------------------------------------------------------------
	// Gameplay help: highlights and warnings

	@ConfigSection(
		name = "Gameplay help",
		description = "Highlights and warnings for what needs doing",
		position = 1
	)
	String helpSection = "help";

	@ConfigItem(
		keyName = "pyromancerCue",
		name = "Downed Pyromancer",
		description = "Outline a nearby Pyromancer that needs healing",
		section = helpSection,
		position = 0
	)
	default boolean pyromancerCue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "brazierCue",
		name = "Broken or unlit brazier",
		description = "Outline a nearby brazier that needs fixing, or lighting during a round",
		section = helpSection,
		position = 1
	)
	default boolean brazierCue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "missingSuppliesCue",
		name = "Missing supplies",
		description = "Outline the crate for any tool you don't have (knife, hammer, axe, tinderbox or bruma torch), the potion crate if you have no potions, and the roots during a round if you have nothing to burn",
		section = helpSection,
		position = 2
	)
	default boolean missingSuppliesCue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "cueLabels",
		name = "Labels",
		description = "Show Heal, Fix or Light above what needs doing",
		section = helpSection,
		position = 3
	)
	default boolean cueLabels()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pointsOverlay",
		name = "Points panel",
		description = "A panel with what you're doing, your points toward 500, inventory points, kindling and the time to the next round",
		section = helpSection,
		position = 4
	)
	default boolean pointsOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "roundStartNotification",
		name = "Round start notification",
		description = "Notify you when the next round is about to start, while you're at the Wintertodt",
		section = helpSection,
		position = 5
	)
	default Notification roundStartNotification()
	{
		return Notification.ON;
	}

	@Range(max = 60)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "roundStartWarning",
		name = "Notify before start",
		description = "How many seconds before the round starts to notify you. 0 notifies as it starts.",
		section = helpSection,
		position = 6
	)
	default int roundStartWarning()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "healSelfCue",
		name = "Low warmth",
		description = "Glow around the edges of the screen when your warmth is low",
		section = helpSection,
		position = 7
	)
	default boolean healSelfCue()
	{
		return true;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(
		keyName = "warmthThreshold",
		name = "Warmth below",
		description = "Warmth percent at or below which the low warmth glow shows",
		section = helpSection,
		position = 8
	)
	default int warmthThreshold()
	{
		return 40;
	}

	@ConfigItem(
		keyName = "warmthColor",
		name = "Low warmth color",
		description = "Color of the glow around the screen when your warmth is low",
		section = helpSection,
		position = 9
	)
	default Color warmthColor()
	{
		return new Color(255, 30, 30);
	}

	@Range(min = 5, max = 100)
	@ConfigItem(
		keyName = "warmthGlowStrength",
		name = "Glow strength",
		description = "How strong the low warmth glow is, in percent",
		section = helpSection,
		position = 10
	)
	default int warmthGlowStrength()
	{
		return 45;
	}

	@ConfigItem(
		keyName = "potionCue",
		name = "Potion ready",
		description = "Highlight rejuvenation potions when a full dose would not be wasted",
		section = helpSection,
		position = 11
	)
	default boolean potionCue()
	{
		return true;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "potionDoseWarmth",
		name = "Dose restores",
		description = "How much warmth (percent) one dose of rejuvenation potion restores",
		section = helpSection,
		position = 12
	)
	default int potionDoseWarmth()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "potionCueColor",
		name = "Potion ready color",
		description = "Color of the outline on rejuvenation potions when they're ready to drink",
		section = helpSection,
		position = 13
	)
	default Color potionCueColor()
	{
		return new Color(120, 255, 140);
	}

	// ---------------------------------------------------------------------------------------
	// Fun: extras just for fun

	@ConfigSection(
		name = "Fun",
		description = "Extras just for fun",
		position = 5
	)
	String funSection = "fun";

	@ConfigItem(
		keyName = "randomColors",
		name = "Random colors each round",
		description = "Pick random colors for the Wintertodt, its snow attacks, the fire, the doors, the roots, the supplies and the highlights, with new ones after every round. The area reloads for a moment when they change. Turn it off to get your own colors back.",
		section = funSection,
		position = 0
	)
	default boolean randomColors()
	{
		return false;
	}

	@ConfigItem(
		keyName = "skiing",
		name = "Skiing",
		description = "Stand, walk and run on skis around the Wintertodt. Your own weapon comes back while you chop, fletch and so on. Only you see it.",
		section = funSection,
		position = 1
	)
	default boolean skiing()
	{
		return false;
	}

	@ConfigItem(
		keyName = "coldBreath",
		name = "Cold breath",
		description = "Puffs of frosty breath from everyone's mouths around the Wintertodt. Yours come faster as your warmth drops. Only you see it.",
		section = funSection,
		position = 2
	)
	default boolean coldBreath()
	{
		return false;
	}
}
