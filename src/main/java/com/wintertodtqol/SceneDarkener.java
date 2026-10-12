package com.wintertodtqol;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import javax.inject.Singleton;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Model;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Darkens and tints the Wintertodt area's floor and scenery as the map loads, fading out
 * over {@link WintertodtIds#FADE_TILES} tiles around it so there's no hard edge.
 *
 * Based on the Dark Wintertodt plugin by kulcris (https://github.com/kulcris/darkwintertodt),
 * MIT License, Copyright (c) 2026 kulcris. See THIRD_PARTY_NOTICES.md.
 *
 * Changes from the original: tints toward a color as well as darkening, darkens every object
 * in the area except braziers and the Wintertodt (instead of a fixed id list), and remembers
 * original model colors so repeated map loads don't keep darkening cached models.
 */
@Singleton
class SceneDarkener
{
	// Floor tiles are rebuilt on every map load, but object models can be cached and reused,
	// so object colors go through this memory and are put back before each load.
	private final ColorMemory objectColors = new ColorMemory();
	// Remap tables by strength level (fading out around the area), built when first needed
	private static final int LEVELS = 12;
	private final int[][] remaps = new int[LEVELS + 1][];
	private double lightnessScale;
	private int tint;
	private boolean darken;
	// How strongly colored things are pulled toward the arena color (white and grey always take its hue)
	private static final double TINT_STRENGTH = 0.5;

	// Per-object recolors (doors, roots) by object id. These objects don't animate, so some
	// renderers (117 HD) upload them once at map load; changing them later has no effect.
	private volatile Map<Integer, IntUnaryOperator> objectRecolors = Collections.emptyMap();

	void setObjectRecolors(Map<Integer, IntUnaryOperator> recolors)
	{
		objectRecolors = recolors;
	}

	/**
	 * Must be called from {@code PreMapLoad}, before the scene is uploaded for drawing.
	 */
	void apply(Scene scene, WintertodtQolConfig config)
	{
		objectColors.restoreAll();
		darken = config.darkenScene();
		if (!darken && objectRecolors.isEmpty())
		{
			return;
		}

		if (darken)
		{
			buildRemap(config);
		}

		Tile[][][] tiles = scene.isInstance() ? scene.getTiles() : scene.getExtendedTiles();
		if (tiles == null)
		{
			return;
		}

		Set<Model> done = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Tile[][] plane : tiles)
		{
			for (Tile[] column : plane)
			{
				for (Tile tile : column)
				{
					WorldPoint world = tile == null ? null : worldOf(scene, tile);
					if (world == null)
					{
						continue;
					}
					// Measured from the middle of the tile
					int level = levelAt(world.getX() + 0.5, world.getY() + 0.5);
					if (level > 0)
					{
						// Bridges are a second tile stacked on top of this one
						for (Tile current = tile; current != null; current = current.getBridge())
						{
							recolorTile(current, done, world, darken ? level : 0);
						}
					}
				}
			}
		}
	}

	/**
	 * Puts back the original object colors. The floor needs a map reload to go back.
	 */
	void restore()
	{
		objectColors.restoreAll();
	}

	private void buildRemap(WintertodtQolConfig config)
	{
		lightnessScale = (100 - config.darkness()) / 100.0;
		tint = Hsl.fromColor(config.arenaColor());
		// Rebuilt as needed for the new settings
		Arrays.fill(remaps, null);
	}

	/**
	 * The strength level (0 to {@link #LEVELS}) of the atmosphere at a world point.
	 */
	private static int levelAt(double x, double y)
	{
		return (int) Math.round(WintertodtIds.atmosphereStrength(x, y) * LEVELS);
	}

	private static WorldPoint worldOf(Scene scene, Tile tile)
	{
		LocalPoint local = tile.getLocalLocation();
		return scene.isInstance()
			? WorldPoint.fromLocalInstance(scene, local, tile.getPlane())
			: WorldPoint.fromLocal(scene, local.getX(), local.getY(), tile.getPlane());
	}

	/**
	 * @param level how strongly to darken this tile, 0 (not at all) to {@link #LEVELS}
	 */
	private void recolorTile(Tile tile, Set<Model> done, WorldPoint world, int level)
	{
		boolean darkenHere = level > 0;
		SceneTilePaint paint = tile.getSceneTilePaint();
		if (darkenHere && paint != null && paint.getTexture() == -1)
		{
			// Each corner by its own strength, so the fade is smooth across the floor
			int x = world.getX();
			int y = world.getY();
			paint.setNwColor(remap(paint.getNwColor(), levelAt(x, y + 1)));
			paint.setNeColor(remap(paint.getNeColor(), levelAt(x + 1, y + 1)));
			paint.setSwColor(remap(paint.getSwColor(), levelAt(x, y)));
			paint.setSeColor(remap(paint.getSeColor(), levelAt(x + 1, y)));
		}

		IntUnaryOperator remapHere = hsl -> remap(hsl, level);
		SceneTileModel tileModel = tile.getSceneTileModel();
		if (darkenHere && tileModel != null)
		{
			int[] textures = tileModel.getTriangleTextureId();
			objectColors.recolor(tileModel.getTriangleColorA(), textures, remapHere);
			objectColors.recolor(tileModel.getTriangleColorB(), textures, remapHere);
			objectColors.recolor(tileModel.getTriangleColorC(), textures, remapHere);
		}

		GroundObject groundObject = tile.getGroundObject();
		if (groundObject != null)
		{
			recolorObject(groundObject, done, remapHere, darkenHere, groundObject.getRenderable());
		}

		WallObject wall = tile.getWallObject();
		if (wall != null)
		{
			recolorObject(wall, done, remapHere, darkenHere, wall.getRenderable1(), wall.getRenderable2());
		}

		DecorativeObject decoration = tile.getDecorativeObject();
		if (decoration != null)
		{
			recolorObject(decoration, done, remapHere, darkenHere, decoration.getRenderable(), decoration.getRenderable2());
		}

		GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects != null)
		{
			for (GameObject gameObject : gameObjects)
			{
				if (gameObject != null)
				{
					recolorObject(gameObject, done, remapHere, darkenHere, gameObject.getRenderable());
				}
			}
		}
	}

	private void recolorObject(TileObject object, Set<Model> done, IntUnaryOperator remap, boolean darkenHere,
		Renderable... renderables)
	{
		IntUnaryOperator recolor = objectRecolors.get(object.getId());
		IntUnaryOperator change;
		if (recolor == null)
		{
			if (!darkenHere || WintertodtIds.KEEP_BRIGHT.contains(object.getId()))
			{
				return;
			}
			change = remap;
		}
		else if (darkenHere)
		{
			// Parts the recolor changes take its color; everything else is still darkened,
			// so these objects (some are wall pieces) match the rest of the area
			change = hsl ->
			{
				int recolored = recolor.applyAsInt(hsl);
				return recolored != hsl ? recolored : remap.applyAsInt(hsl);
			};
		}
		else
		{
			change = recolor;
		}

		for (Renderable renderable : renderables)
		{
			// PreMapLoad runs on the map loader thread, where only plain models are safe to touch.
			// Animated objects build their model on demand, which must happen on the client thread,
			// so they're skipped (getModel() throws "must be called on client thread").
			if (renderable instanceof Model && done.add((Model) renderable))
			{
				objectColors.recolor((Model) renderable, change);
			}
		}
	}

	private int remap(int hsl, int level)
	{
		if (level <= 0 || !Hsl.isColor(hsl))
		{
			return hsl;
		}

		int[] table = remaps[level];
		if (table == null)
		{
			// A weaker darken and tint toward the edges of the fade
			double strength = (double) level / LEVELS;
			double scale = 1 - (1 - lightnessScale) * strength;
			table = new int[Hsl.MAX + 1];
			for (int color = 0; color <= Hsl.MAX; color++)
			{
				table[color] = Hsl.darkenAndTint(color, scale, tint, TINT_STRENGTH * strength);
			}
			remaps[level] = table;
		}
		return table[hsl];
	}
}
