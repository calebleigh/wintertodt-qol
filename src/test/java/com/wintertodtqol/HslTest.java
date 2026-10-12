package com.wintertodtqol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class HslTest
{
	@Test
	public void packAndUnpack()
	{
		int hsl = Hsl.pack(40, 5, 100);
		assertEquals(40, Hsl.hue(hsl));
		assertEquals(5, Hsl.saturation(hsl));
		assertEquals(100, Hsl.lightness(hsl));
	}

	@Test
	public void blendHueTakesTheShortWayAround()
	{
		// 62 -> 2 is 4 steps forward through 63 and 0, not 60 steps back
		assertEquals(0, Hsl.blendHue(62, 2, 0.5));
		assertEquals(2, Hsl.blendHue(62, 2, 1.0));
		assertEquals(62, Hsl.blendHue(62, 2, 0.0));
	}

	@Test
	public void darkenAndTintDarkensWhiteAndGivesItTheTintHue()
	{
		int white = Hsl.pack(0, 0, 120);
		int tint = Hsl.pack(42, 4, 60);
		int result = Hsl.darkenAndTint(white, 0.5, tint, 0.5);
		assertEquals(60, Hsl.lightness(result));
		assertEquals(42, Hsl.hue(result));
		assertEquals(2, Hsl.saturation(result));
	}

	@Test
	public void darkenNeverGoesFullyBlack()
	{
		assertEquals(1, Hsl.lightness(Hsl.darkenAndTint(Hsl.pack(10, 3, 5), 0.0, 0, 0)));
	}

	@Test
	public void recolorKeepsLightness()
	{
		int fire = Hsl.pack(6, 7, 90);
		int purple = Hsl.pack(48, 5, 40);
		int result = Hsl.withHueAndSaturation(fire, purple);
		assertEquals(48, Hsl.hue(result));
		assertEquals(5, Hsl.saturation(result));
		assertEquals(90, Hsl.lightness(result));
	}

	@Test
	public void recolorWhiteBringsLightnessIntoRangeAndKeepsShading()
	{
		int red = Hsl.pack(0, 6, 70);
		int brightSnow = Hsl.recolorWhite(Hsl.pack(38, 0, 120), red);
		int dimSnow = Hsl.recolorWhite(Hsl.pack(38, 0, 60), red);
		assertEquals(0, Hsl.hue(brightSnow));
		// Always fully saturated so the color stands out
		assertEquals(7, Hsl.saturation(brightSnow));
		assertTrue(Hsl.lightness(brightSnow) <= 95);
		assertTrue(Hsl.lightness(dimSnow) < Hsl.lightness(brightSnow));
	}

	@Test
	public void tintGlowStaysSoft()
	{
		int purple = Hsl.pack(48, 6, 60);
		int glow = Hsl.tintGlow(Hsl.pack(24, 1, 100), purple);
		assertEquals(48, Hsl.hue(glow));
		// Keeps its own low saturation, and is a little dimmer
		assertEquals(1, Hsl.saturation(glow));
		assertEquals(85, Hsl.lightness(glow));
	}

	@Test
	public void greenDetection()
	{
		// The Wintertodt brazier flames and door glow
		assertTrue(Hsl.isGreen(Hsl.pack(14, 7, 80)));
		assertTrue(Hsl.isGreen(Hsl.pack(17, 7, 80)));
		// Low-saturation stone is not
		assertFalse(Hsl.isGreen(Hsl.pack(24, 1, 80)));
	}

	@Test
	public void fireDetection()
	{
		assertTrue(Hsl.isFire(Hsl.pack(6, 7, 90)));
		assertTrue(Hsl.isFire(Hsl.pack(63, 5, 60)));
		// Grey metal and blue are not fire
		assertFalse(Hsl.isFire(Hsl.pack(6, 0, 60)));
		assertFalse(Hsl.isFire(Hsl.pack(40, 6, 60)));
	}

	@Test
	public void markersAreNotColors()
	{
		assertFalse(Hsl.isColor(-1));
		assertFalse(Hsl.isColor(-2));
		assertFalse(Hsl.isColor(12345678));
		assertTrue(Hsl.isColor(0));
	}
}
