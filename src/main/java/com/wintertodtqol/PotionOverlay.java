package com.wintertodtqol;

import com.google.common.collect.ImmutableSet;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Pulses an outline around rejuvenation potions in the inventory when warmth is low enough
 * that a whole dose would go to use.
 */
class PotionOverlay extends WidgetItemOverlay
{
	private static final Set<Integer> POTIONS = ImmutableSet.of(
		ItemID.WINT_POTION1,
		ItemID.WINT_POTION2,
		ItemID.WINT_POTION3,
		ItemID.WINT_POTION4
	);

	private static final int PULSE_PERIOD_MS = 1000;

	private final ItemManager itemManager;
	private final WintertodtQolPlugin plugin;
	private final WintertodtQolConfig config;

	@Inject
	PotionOverlay(ItemManager itemManager, WintertodtQolPlugin plugin, WintertodtQolConfig config)
	{
		this.itemManager = itemManager;
		this.plugin = plugin;
		this.config = config;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.potionCue() || !POTIONS.contains(itemId) || !isDoseUseful())
		{
			return;
		}

		double phase = (System.currentTimeMillis() % PULSE_PERIOD_MS) / (double) PULSE_PERIOD_MS;
		int alpha = (int) (140 + 115 * (0.5 + 0.5 * Math.sin(phase * 2 * Math.PI)));
		Color ready = config.potionCueColor();
		Color color = new Color(ready.getRed(), ready.getGreen(), ready.getBlue(), alpha);

		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), color);
		graphics.drawImage(outline, bounds.x, bounds.y, null);
	}

	/**
	 * True when warmth is low enough that a whole dose goes to use.
	 */
	private boolean isDoseUseful()
	{
		int warmth = plugin.getWarmthPercent();
		return plugin.isInArena() && warmth >= 0 && warmth <= 100 - config.potionDoseWarmth();
	}
}
