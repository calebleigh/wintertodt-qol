package com.wintertodtqol;

import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ModelData;
import net.runelite.api.gameval.ItemID;

/**
 * Recolors the Wintertodt supply items (roots, kindling, herbs, finished potions) by changing
 * the item definitions as the game loads them. The game then draws the icons itself, so
 * outlines, the see-through "selected" look and dragging all work as normal, and dropped or
 * held items match too.
 *
 * The client caches item definitions, models and icons, so {@link #reload} clears those caches
 * whenever the recolor changes. Client thread only.
 */
@Singleton
class SupplyRecolor
{
	// The unfinished potion (WINT_VIAL) keeps its own color so it stands apart from finished ones
	static final Set<Integer> SUPPLIES = ImmutableSet.of(
		ItemID.WINT_BRUMA_ROOT,
		ItemID.WINT_BRUMA_KINDLING,
		ItemID.WINT_HERB,
		ItemID.WINT_POTION1,
		ItemID.WINT_POTION2,
		ItemID.WINT_POTION3,
		ItemID.WINT_POTION4
	);

	// Faces less saturated than this (glass, grey, outlines) keep their color
	private static final int MIN_SATURATION = 2;

	private final Client client;

	// The fire color as packed HSL, or -1 when supplies aren't recolored
	private int fire = -1;

	@Inject
	SupplyRecolor(Client client)
	{
		this.client = client;
	}

	/**
	 * Sets the color to recolor supplies to (packed HSL), or -1 to stop, and reloads the items.
	 */
	void setColor(int fireHsl)
	{
		if (fireHsl != fire)
		{
			fire = fireHsl;
			reload();
		}
	}

	/**
	 * Called for every item definition the game loads ({@code PostItemComposition}).
	 */
	void onItemLoaded(ItemComposition item)
	{
		if (fire < 0 || !SUPPLIES.contains(item.getId()))
		{
			return;
		}

		ModelData model = client.loadModelData(item.getInventoryModel());
		if (model == null)
		{
			return;
		}

		// Colors this item already swaps (potions share one vial model, recolored per potion),
		// with their replacement recolored too, then every other colorful face of the model
		Map<Short, Short> swaps = new LinkedHashMap<>();
		short[] from = item.getColorToReplace();
		short[] to = item.getColorToReplaceWith();
		if (from != null && to != null)
		{
			for (int i = 0; i < from.length && i < to.length; i++)
			{
				swaps.put(from[i], recolor(to[i]));
			}
		}
		for (short color : model.getFaceColors())
		{
			if (!swaps.containsKey(color))
			{
				short recolored = recolor(color);
				if (recolored != color)
				{
					swaps.put(color, recolored);
				}
			}
		}

		short[] newFrom = new short[swaps.size()];
		short[] newTo = new short[swaps.size()];
		int i = 0;
		for (Map.Entry<Short, Short> swap : swaps.entrySet())
		{
			newFrom[i] = swap.getKey();
			newTo[i] = swap.getValue();
			i++;
		}
		item.setColorToReplace(newFrom);
		item.setColorToReplaceWith(newTo);
	}

	private short recolor(short color)
	{
		int hsl = color & Hsl.MAX;
		return Hsl.saturation(hsl) >= MIN_SATURATION ? (short) Hsl.withHueAndSaturation(hsl, fire) : color;
	}

	/**
	 * Throws away cached item definitions, models and icons so they load again with the
	 * current recolor (or none).
	 */
	void reload()
	{
		client.getItemCompositionCache().reset();
		client.getItemModelCache().reset();
		client.getItemSpriteCache().reset();
	}
}
