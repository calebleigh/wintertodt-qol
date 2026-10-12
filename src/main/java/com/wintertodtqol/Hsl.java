package com.wintertodtqol;

import java.awt.Color;
import net.runelite.api.JagexColor;

/**
 * Helpers for the game's packed 16-bit HSL colors, used by models and floor tiles:
 * 6 bits hue (0-63), 3 bits saturation (0-7), 7 bits lightness (0-127).
 *
 * Values outside 0..0xFFFF are special markers (for example -1 "flat shaded" and -2 "hidden face")
 * and must never be changed.
 */
final class Hsl
{
	static final int MAX = 0xFFFF;

	private static final int HUE_COUNT = 64;
	// Red, orange and yellow. Hue 0 is red and yellow is around 10.
	private static final int FIRE_HUE_MAX = 12;
	private static final int FIRE_HUE_MIN_WRAPPED = 61;
	private static final int FIRE_SATURATION_MIN = 2;
	// Yellow-green to blue-green. Pure green is about 21.
	private static final int GREEN_HUE_MIN = 14;
	private static final int GREEN_HUE_MAX = 30;
	// How much a recolored faint glow is dimmed
	private static final double GLOW_LIGHTNESS_SCALE = 0.85;
	// Lightness range white things are squeezed into so a recolor shows (of 127)
	private static final int MIN_SHOWN_LIGHTNESS = 45;
	private static final int MAX_SHOWN_LIGHTNESS = 95;
	private static final int MIN_SHOWN_SATURATION = 7;

	private Hsl()
	{
	}

	static boolean isColor(int hsl)
	{
		return hsl >= 0 && hsl <= MAX;
	}

	static int hue(int hsl)
	{
		return (hsl >> 10) & 0x3F;
	}

	static int saturation(int hsl)
	{
		return (hsl >> 7) & 0x7;
	}

	static int lightness(int hsl)
	{
		return hsl & 0x7F;
	}

	static int pack(int hue, int saturation, int lightness)
	{
		return (hue << 10) | (saturation << 7) | lightness;
	}

	static int fromColor(Color color)
	{
		return JagexColor.rgbToHSL(color.getRGB() & 0xFFFFFF, 1.0) & MAX;
	}

	/**
	 * The atmosphere change: scale lightness down and pull hue and saturation toward the tint.
	 * Grey and white (saturation 0) take the tint's hue outright, since they have no hue of their own.
	 */
	static int darkenAndTint(int hsl, double lightnessScale, int tint, double tintStrength)
	{
		int lightness = clamp((int) Math.ceil(lightness(hsl) * lightnessScale), 1, 127);
		int saturation = clamp((int) Math.round(saturation(hsl) + (saturation(tint) - saturation(hsl)) * tintStrength), 0, 7);
		double hueAmount = saturation(hsl) == 0 ? 1.0 : tintStrength;
		int hue = blendHue(hue(hsl), hue(tint), hueAmount);
		return pack(hue, saturation, lightness);
	}

	/**
	 * Gives a color the hue and saturation of the target but keeps its own lightness,
	 * so shading and highlights survive the recolor.
	 */
	static int withHueAndSaturation(int hsl, int target)
	{
		return pack(hue(target), saturation(target), lightness(hsl));
	}

	/**
	 * Recolors something near-white so the color actually shows: takes the target's hue, at
	 * least a clear saturation, and squeezes lightness into a middle range (keeping the shading).
	 * At white-level lightness every hue looks white.
	 */
	static int recolorWhite(int hsl, int target)
	{
		int lightness = MIN_SHOWN_LIGHTNESS + lightness(hsl) * (MAX_SHOWN_LIGHTNESS - MIN_SHOWN_LIGHTNESS) / 127;
		int saturation = Math.max(saturation(target), MIN_SHOWN_SATURATION);
		return pack(hue(target), saturation, lightness);
	}

	/**
	 * Gives a faint glow the target's hue but keeps it soft: its own (low) saturation, and a
	 * little dimmer, so it stays a glow instead of becoming a hard, bright layer.
	 */
	static int tintGlow(int hsl, int target)
	{
		int lightness = (int) Math.round(lightness(hsl) * GLOW_LIGHTNESS_SCALE);
		return pack(hue(target), Math.min(saturation(hsl), saturation(target)), lightness);
	}

	/**
	 * Roughly "looks like fire": a saturated red, orange or yellow.
	 */
	static boolean isFire(int hsl)
	{
		int hue = hue(hsl);
		return saturation(hsl) >= FIRE_SATURATION_MIN && (hue <= FIRE_HUE_MAX || hue >= FIRE_HUE_MIN_WRAPPED);
	}

	/**
	 * Roughly "looks green", like the glow on the Doors of Dinh.
	 */
	static boolean isGreen(int hsl)
	{
		int hue = hue(hsl);
		return saturation(hsl) >= FIRE_SATURATION_MIN && hue >= GREEN_HUE_MIN && hue <= GREEN_HUE_MAX;
	}

	/**
	 * Faintly colored, warm to green (not the grey-blue of stone and snow).
	 * Catches the dim green tint on the burning brazier's body.
	 */
	static boolean isTinted(int hsl)
	{
		int hue = hue(hsl);
		return saturation(hsl) >= 1 && (hue <= GREEN_HUE_MAX || hue >= FIRE_HUE_MIN_WRAPPED);
	}

	/**
	 * Moves a hue toward another by the given amount (0-1), taking the short way around the color wheel.
	 */
	static int blendHue(int from, int to, double amount)
	{
		int difference = Math.floorMod(to - from + HUE_COUNT / 2, HUE_COUNT) - HUE_COUNT / 2;
		return Math.floorMod((int) Math.round(from + difference * amount), HUE_COUNT);
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
