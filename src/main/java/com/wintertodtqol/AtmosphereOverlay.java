package com.wintertodtqol;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Random;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The storm drawn over the 3D view (under the interface): a tinted dim, dark edges and blowing snow.
 */
class AtmosphereOverlay extends Overlay
{
	// Snowflake fall speed in viewport heights per second, and how far wind pushes them sideways
	private static final float MIN_FALL_SPEED = 0.10f;
	private static final float MAX_FALL_SPEED = 0.30f;
	private static final float MAX_WIND_SPEED = 0.45f;
	// Fixed look: how much the view is dimmed, how dark the edges get (percent), and the wind (0-1)
	private static final int DIM_PERCENT = 20;
	private static final int VIGNETTE_PERCENT = 50;
	private static final float WIND = 0.4f;
	// Longest frame gap we simulate, so the snow doesn't jump after a stall
	private static final float MAX_STEP_SECONDS = 0.1f;

	private final Client client;
	private final WintertodtQolPlugin plugin;
	private final WintertodtQolConfig config;
	private final Random random = new Random();

	// Snowflakes, positions in 0-1 viewport units so they survive resizing
	private float[] flakeX = new float[0];
	private float[] flakeY = new float[0];
	private float[] flakeSpeed = new float[0];
	private float[] flakeSize = new float[0];
	// Whether each flake is falling. Flakes only start or stop as they come back round to the
	// top, so the snow eases in when a round starts and eases off when it ends.
	private boolean[] flakeOn = new boolean[0];
	private long lastFrameNanos;
	// How strong the storm is where the player stands (0-1), fading out around the area
	private double strength;

	@Inject
	AtmosphereOverlay(Client client, WintertodtQolPlugin plugin, WintertodtQolConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		strength = plugin.getAtmosphereStrength();
		if (!config.stormOverlay() || strength <= 0)
		{
			lastFrameNanos = 0;
			return null;
		}

		Rectangle view = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
			client.getViewportWidth(), client.getViewportHeight());
		if (view.width <= 0 || view.height <= 0)
		{
			return null;
		}

		Shape oldClip = graphics.getClip();
		graphics.clip(view);

		drawDim(graphics, view);
		drawSnow(graphics, view);
		drawVignette(graphics, view);

		graphics.setClip(oldClip);
		return null;
	}

	private void drawDim(Graphics2D graphics, Rectangle view)
	{
		int alpha = (int) (DIM_PERCENT * 255 / 100 * strength);
		if (alpha <= 0)
		{
			return;
		}

		// A very dark version of the storm tint, so dimming also cools the colors
		Color tint = config.arenaColor();
		graphics.setColor(new Color(tint.getRed() / 4, tint.getGreen() / 4, tint.getBlue() / 4, alpha));
		graphics.fill(view);
	}

	private void drawVignette(Graphics2D graphics, Rectangle view)
	{
		int alpha = (int) (VIGNETTE_PERCENT * 230 / 100 * strength);
		if (alpha <= 0)
		{
			return;
		}

		float radius = (float) Math.hypot(view.width, view.height) / 2f;
		RadialGradientPaint paint = new RadialGradientPaint(
			(float) view.getCenterX(), (float) view.getCenterY(), radius,
			new float[]{0.45f, 1f},
			new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, alpha)},
			MultipleGradientPaint.CycleMethod.NO_CYCLE);
		graphics.setPaint(paint);
		graphics.fill(view);
	}

	private void drawSnow(Graphics2D graphics, Rectangle view)
	{
		// Snowing only while the Wintertodt is raging, thinning out toward the edge of the storm
		double density = plugin.isStormActive() ? strength : 0;
		int count = config.blowingSnow() * 10;
		if (count != flakeX.length)
		{
			createFlakes(count, density);
		}
		if (count == 0)
		{
			return;
		}

		long now = System.nanoTime();
		float seconds = lastFrameNanos == 0 ? 0 : Math.min(MAX_STEP_SECONDS, (now - lastFrameNanos) / 1e9f);
		lastFrameNanos = now;

		float wind = WIND * MAX_WIND_SPEED;
		// Scale flakes with the view so they look the same on big and small screens
		float scale = Math.max(1f, view.height / 500f);

		for (int i = 0; i < count; i++)
		{
			// Faster flakes are "closer", so they also blow sideways more
			float depth = flakeSpeed[i] / MAX_FALL_SPEED;
			flakeY[i] += flakeSpeed[i] * seconds;
			flakeX[i] += wind * depth * seconds;
			if (flakeY[i] > 1f)
			{
				flakeY[i] -= 1f;
				flakeX[i] = random.nextFloat();
				flakeOn[i] = random.nextFloat() < density;
			}
			flakeX[i] -= (float) Math.floor(flakeX[i]);

			if (!flakeOn[i])
			{
				continue;
			}
			int size = Math.max(1, Math.round(flakeSize[i] * scale));
			int alpha = (int) (90 + 120 * depth);
			graphics.setColor(new Color(235, 240, 255, Math.min(255, alpha)));
			graphics.fillOval(view.x + (int) (flakeX[i] * view.width), view.y + (int) (flakeY[i] * view.height), size, size);
		}
	}

	private void createFlakes(int count, double density)
	{
		flakeOn = new boolean[count];
		flakeX = new float[count];
		flakeY = new float[count];
		flakeSpeed = new float[count];
		flakeSize = new float[count];
		for (int i = 0; i < count; i++)
		{
			flakeX[i] = random.nextFloat();
			flakeY[i] = random.nextFloat();
			flakeSpeed[i] = MIN_FALL_SPEED + random.nextFloat() * (MAX_FALL_SPEED - MIN_FALL_SPEED);
			flakeSize[i] = 1.5f + 2.5f * (flakeSpeed[i] / MAX_FALL_SPEED);
			flakeOn[i] = random.nextFloat() < density;
		}
	}
}
