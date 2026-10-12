package com.wintertodtqol;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Highlights in the arena: the falling snow, downed Pyromancers, braziers that need fixing or
 * lighting, and a pulsing screen edge when warmth is low.
 */
class CueOverlay extends Overlay
{
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 1;
	// Only Pyromancers and braziers this close (in tiles) are outlined
	private static final int CUE_RANGE = 10;
	private static final int PULSE_PERIOD_MS = 1200;
	private static final int EDGE_GLOW_PX = 70;

	private final Client client;
	private final WintertodtQolPlugin plugin;
	private final WintertodtQolConfig config;
	private final ModelOutlineRenderer outlineRenderer;

	@Inject
	CueOverlay(Client client, WintertodtQolPlugin plugin, WintertodtQolConfig config, ModelOutlineRenderer outlineRenderer)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.outlineRenderer = outlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		// Above the storm overlay, still under the interface
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isInArena())
		{
			return null;
		}

		if (config.glowSnowfall())
		{
			// Strength sets both how solid and how wide the outline is
			double strength = config.snowOutlineStrength() / 100.0;
			// The outline uses the Wintertodt's second accent, to stand out against the snow (accent 1)
			Color snowOutline = withAlpha(plugin.getWintertodtAccent2Color(), (int) (60 + 195 * strength));
			int width = 1 + (int) Math.round(5 * strength);
			for (GameObject snow : plugin.getFallingSnow())
			{
				try
				{
					outlineRenderer.drawOutline(snow, width, snowOutline, OUTLINE_FEATHER + 1);
				}
				catch (RuntimeException e)
				{
					// The snow's model can fail to build for a moment as it disappears; skip it
				}
			}
		}

		Color fire = plugin.getFireColor();
		Player local = client.getLocalPlayer();
		WorldPoint me = local == null ? null : local.getWorldLocation();

		if (config.pyromancerCue())
		{
			for (NPC pyromancer : plugin.getDownedPyromancers())
			{
				if (isInRange(me, pyromancer.getWorldLocation()))
				{
					outlineRenderer.drawOutline(pyromancer, OUTLINE_WIDTH, fire, OUTLINE_FEATHER);
					drawLabel(graphics, pyromancer.getCanvasTextLocation(graphics, "Heal", pyromancer.getLogicalHeight() + 40), "Heal", fire);
				}
			}
		}

		if (config.brazierCue())
		{
			drawBraziers(graphics, plugin.getBrokenBraziers(), "Fix", fire, me);
			drawBraziers(graphics, plugin.getBraziersNeedingLight(), "Light", fire, me);
		}

		if (config.missingSuppliesCue())
		{
			for (Map.Entry<GameObject, String> supply : plugin.getMissingSupplies().entrySet())
			{
				GameObject object = supply.getKey();
				// Crates are by the entrance, so they show from anywhere; roots only nearby
				boolean roots = object.getId() == WintertodtIds.BRUMA_ROOTS;
				if (!roots || isInRange(me, object.getWorldLocation()))
				{
					outlineRenderer.drawOutline(object, OUTLINE_WIDTH, fire, OUTLINE_FEATHER);
					drawLabel(graphics, object.getCanvasTextLocation(graphics, supply.getValue(), 120), supply.getValue(), fire);
				}
			}
		}

		if (config.healSelfCue())
		{
			int warmth = plugin.getWarmthPercent();
			if (warmth >= 0 && warmth <= config.warmthThreshold() && plugin.isRoundActive())
			{
				drawLowWarmth(config.warmthColor(), graphics);
			}
		}

		return null;
	}

	private void drawBraziers(Graphics2D graphics, Iterable<GameObject> braziers, String label, Color color, WorldPoint me)
	{
		for (GameObject brazier : braziers)
		{
			if (isInRange(me, brazier.getWorldLocation()))
			{
				outlineRenderer.drawOutline(brazier, OUTLINE_WIDTH, color, OUTLINE_FEATHER);
				drawLabel(graphics, brazier.getCanvasTextLocation(graphics, label, 150), label, color);
			}
		}
	}

	/**
	 * A pulsing glow along each edge of the game view, fading inward.
	 */
	private void drawLowWarmth(Color color, Graphics2D graphics)
	{
		Rectangle view = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
			client.getViewportWidth(), client.getViewportHeight());

		// The strength setting scales both how opaque and how wide it is
		double strength = config.warmthGlowStrength() / 100.0;
		double phase = (System.currentTimeMillis() % PULSE_PERIOD_MS) / (double) PULSE_PERIOD_MS;
		int alpha = (int) ((90 + 130 * (0.5 + 0.5 * Math.sin(phase * 2 * Math.PI))) * strength);
		Color solid = withAlpha(color, alpha);
		Color clear = withAlpha(color, 0);
		int glow = (int) Math.min(EDGE_GLOW_PX * (0.5 + strength), Math.min(view.width, view.height) / 4.0);

		graphics.setPaint(new GradientPaint(view.x, 0, solid, view.x + glow, 0, clear));
		graphics.fillRect(view.x, view.y, glow, view.height);
		graphics.setPaint(new GradientPaint(view.x + view.width, 0, solid, view.x + view.width - glow, 0, clear));
		graphics.fillRect(view.x + view.width - glow, view.y, glow, view.height);
		graphics.setPaint(new GradientPaint(0, view.y, solid, 0, view.y + glow, clear));
		graphics.fillRect(view.x, view.y, view.width, glow);
		graphics.setPaint(new GradientPaint(0, view.y + view.height, solid, 0, view.y + view.height - glow, clear));
		graphics.fillRect(view.x, view.y + view.height - glow, view.width, glow);
	}

	private void drawLabel(Graphics2D graphics, Point position, String text, Color color)
	{
		if (config.cueLabels() && position != null)
		{
			graphics.setFont(FontManager.getRunescapeBoldFont());
			OverlayUtil.renderTextLocation(graphics, position, text, color);
		}
	}

	private static boolean isInRange(WorldPoint me, WorldPoint target)
	{
		return me != null && target != null && me.distanceTo(target) <= CUE_RANGE;
	}

	private static Color withAlpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
	}
}
