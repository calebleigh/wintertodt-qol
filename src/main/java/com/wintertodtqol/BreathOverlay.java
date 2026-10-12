package com.wintertodtqol;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Puffs of frosty breath from everyone's mouths around the Wintertodt. Yours come faster as
 * your warmth drops.
 *
 * Puffs live in the 3D world (local coordinates) and are projected to the screen each frame,
 * so they stay where they were breathed out when you move.
 */
class BreathOverlay extends Overlay
{
	// Seconds between breaths: others at random in this range, yours from slow (warm) to fast (cold)
	private static final float OTHERS_MIN_GAP = 2.2f;
	private static final float OTHERS_MAX_GAP = 3.6f;
	private static final float WARM_GAP = 3.2f;
	private static final float COLD_GAP = 1.1f;
	// How long a puff lasts, in seconds
	private static final float PUFF_LIFE = 1.6f;
	// Mouth position: share of the player's height, and how far in front of them (local units, 128 per tile)
	private static final double MOUTH_HEIGHT = 0.72;
	private static final int MOUTH_FORWARD = 24;
	// Puff size in local units, as it leaves the mouth and when it fades out
	private static final float START_SIZE = 10;
	private static final float END_SIZE = 42;
	// Most puffs drawn at once, to keep busy worlds cheap
	private static final int MAX_PUFFS = 300;
	private static final float MAX_STEP_SECONDS = 0.1f;

	private final Client client;
	private final WintertodtQolPlugin plugin;
	private final WintertodtQolConfig config;
	private final Random random = new Random();

	private final List<Puff> puffs = new ArrayList<>();
	// Seconds until each player's next breath
	private final Map<Player, Float> nextBreath = new WeakHashMap<>();
	private long lastFrameNanos;

	private static class Puff
	{
		// Local coordinates; height is above the ground
		float x;
		float y;
		float height;
		float vx;
		float vy;
		float vHeight;
		float age;
		int plane;
	}

	@Inject
	BreathOverlay(Client client, WintertodtQolPlugin plugin, WintertodtQolConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.coldBreath() || plugin.getAtmosphereStrength() <= 0)
		{
			puffs.clear();
			lastFrameNanos = 0;
			return null;
		}

		long now = System.nanoTime();
		float seconds = lastFrameNanos == 0 ? 0 : Math.min(MAX_STEP_SECONDS, (now - lastFrameNanos) / 1e9f);
		lastFrameNanos = now;

		breathe(seconds);
		drawPuffs(graphics, seconds);
		return null;
	}

	private void breathe(float seconds)
	{
		WorldView world = client.getTopLevelWorldView();
		Player local = client.getLocalPlayer();
		if (world == null || local == null)
		{
			return;
		}

		for (Player player : world.players())
		{
			if (player == null)
			{
				continue;
			}

			Float left = nextBreath.get(player);
			if (left == null)
			{
				// Start everyone at a random point in their breathing
				left = random.nextFloat() * OTHERS_MAX_GAP;
			}
			left -= seconds;
			if (left <= 0)
			{
				breatheOut(player, world.getPlane());
				left = player == local ? localGap() : OTHERS_MIN_GAP + random.nextFloat() * (OTHERS_MAX_GAP - OTHERS_MIN_GAP);
			}
			nextBreath.put(player, left);
		}
	}

	/**
	 * Seconds between your breaths: faster the colder you are.
	 */
	private float localGap()
	{
		int warmth = plugin.getWarmthPercent();
		float cold = warmth < 0 ? 0 : 1 - warmth / 100f;
		return WARM_GAP + (COLD_GAP - WARM_GAP) * cold;
	}

	private void breatheOut(Player player, int plane)
	{
		LocalPoint where = player.getLocalLocation();
		if (where == null || puffs.size() >= MAX_PUFFS)
		{
			return;
		}

		// Orientation: 0 faces south, 512 west, 1024 north, 1536 east (2048 per turn)
		double angle = player.getCurrentOrientation() * Math.PI * 2 / 2048;
		float forwardX = (float) -Math.sin(angle);
		float forwardY = (float) -Math.cos(angle);
		float mouthHeight = (float) (player.getLogicalHeight() * MOUTH_HEIGHT);

		// A few small puffs together make one soft cloud
		for (int i = 0; i < 3; i++)
		{
			Puff puff = new Puff();
			puff.x = where.getX() + forwardX * MOUTH_FORWARD;
			puff.y = where.getY() + forwardY * MOUTH_FORWARD;
			puff.height = mouthHeight + random.nextFloat() * 6 - 3;
			float speed = 45 + random.nextFloat() * 30;
			float spread = (random.nextFloat() - 0.5f) * 0.5f;
			puff.vx = (forwardX - forwardY * spread) * speed;
			puff.vy = (forwardY + forwardX * spread) * speed;
			puff.vHeight = 10 + random.nextFloat() * 12;
			puff.age = -i * 0.08f;
			puff.plane = plane;
			puffs.add(puff);
		}
	}

	private void drawPuffs(Graphics2D graphics, float seconds)
	{
		for (Iterator<Puff> it = puffs.iterator(); it.hasNext(); )
		{
			Puff puff = it.next();
			puff.age += seconds;
			if (puff.age >= PUFF_LIFE)
			{
				it.remove();
				continue;
			}
			if (puff.age < 0)
			{
				continue;
			}

			// Drifts out and up, slowing down as it spreads
			float drag = (float) Math.pow(0.25, seconds);
			puff.x += puff.vx * seconds;
			puff.y += puff.vy * seconds;
			puff.height += puff.vHeight * seconds;
			puff.vx *= drag;
			puff.vy *= drag;

			float life = puff.age / PUFF_LIFE;
			float size = START_SIZE + (END_SIZE - START_SIZE) * (float) Math.sqrt(life);
			LocalPoint point = new LocalPoint((int) puff.x, (int) puff.y);
			int ground = Perspective.getTileHeight(client, point, puff.plane);
			Point center = Perspective.localToCanvas(client, (int) puff.x, (int) puff.y, ground - (int) puff.height);
			Point top = Perspective.localToCanvas(client, (int) puff.x, (int) puff.y, ground - (int) (puff.height + size));
			if (center == null || top == null)
			{
				continue;
			}

			float radius = Math.max(2, Math.abs(center.getY() - top.getY()));
			// Quick to appear, slow to fade
			float fade = life < 0.15f ? life / 0.15f : 1 - (life - 0.15f) / 0.85f;
			int alpha = (int) (110 * fade);
			if (alpha <= 0)
			{
				continue;
			}

			RadialGradientPaint paint = new RadialGradientPaint(center.getX(), center.getY(), radius,
				new float[]{0f, 1f},
				new Color[]{new Color(235, 242, 255, alpha), new Color(235, 242, 255, 0)});
			graphics.setPaint(paint);
			graphics.fill(new Ellipse2D.Float(center.getX() - radius, center.getY() - radius, radius * 2, radius * 2));
		}
	}
}
