package com.wintertodtqol;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntUnaryOperator;
import net.runelite.api.Model;

/**
 * Remembers the original contents of every color array we change. Recoloring always starts
 * from the originals, so doing it again (every frame, or after a config change) gives the same
 * result instead of stacking up, and {@link #restoreAll} puts everything back.
 *
 * Arrays are keyed by identity (arrays don't override equals) and held weakly, so models the
 * game throws away are forgotten. Client thread only.
 */
class ColorMemory
{
	private static final short NO_COLOR_SHORT_FLAT = -1;
	private static final short NO_COLOR_SHORT_HIDDEN = -2;

	private final Map<int[], int[]> intOriginals = new WeakHashMap<>();
	private final Map<short[], short[]> shortOriginals = new WeakHashMap<>();

	void recolor(Model model, IntUnaryOperator change)
	{
		if (model == null)
		{
			return;
		}

		recolor(model.getFaceColors1(), null, change);
		recolor(model.getFaceColors2(), null, change);
		recolor(model.getFaceColors3(), null, change);
		recolor(model.getUnlitFaceColors(), change);
	}

	/**
	 * @param textures optional texture id per entry; textured entries (id != -1) are left alone
	 */
	void recolor(int[] colors, int[] textures, IntUnaryOperator change)
	{
		if (colors == null)
		{
			return;
		}

		int[] originals = intOriginals.computeIfAbsent(colors, int[]::clone);
		for (int i = 0; i < colors.length && i < originals.length; i++)
		{
			boolean textured = textures != null && i < textures.length && textures[i] != -1;
			int original = originals[i];
			colors[i] = !textured && Hsl.isColor(original) ? change.applyAsInt(original) : original;
		}
	}

	private void recolor(short[] colors, IntUnaryOperator change)
	{
		if (colors == null)
		{
			return;
		}

		short[] originals = shortOriginals.computeIfAbsent(colors, short[]::clone);
		for (int i = 0; i < colors.length && i < originals.length; i++)
		{
			short original = originals[i];
			boolean marker = original == NO_COLOR_SHORT_FLAT || original == NO_COLOR_SHORT_HIDDEN;
			colors[i] = marker ? original : (short) change.applyAsInt(original & Hsl.MAX);
		}
	}

	/**
	 * The original contents of a color array we changed, or the array itself if we haven't.
	 */
	int[] originalOf(int[] colors)
	{
		return intOriginals.getOrDefault(colors, colors);
	}

	void restoreAll()
	{
		intOriginals.forEach((colors, originals) -> System.arraycopy(originals, 0, colors, 0, Math.min(colors.length, originals.length)));
		shortOriginals.forEach((colors, originals) -> System.arraycopy(originals, 0, colors, 0, Math.min(colors.length, originals.length)));
		intOriginals.clear();
		shortOriginals.clear();
	}
}
