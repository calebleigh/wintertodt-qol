package com.wintertodtqol;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.SpotanimID;

/**
 * Wintertodt regions and the game ids this plugin cares about.
 */
final class WintertodtIds
{
	// The arena itself, and the camp just south of it (bank, doors)
	static final int ARENA_REGION = 6462;
	static final int CAMP_REGION = 6461;

	static final int BRAZIER_UNLIT = ObjectID.WINT_BRAZIER;
	static final int BRAZIER_BROKEN = ObjectID.WINT_BRAZIER_BROKEN;
	static final int BRAZIER_LIT = ObjectID.WINT_BRAZIER_LIT;

	// The Wintertodt is a "snow storm" object: howling while a round is on, quiescent between rounds
	static final int STORM_ACTIVE = ObjectID.WINT_SNOW_STORM;
	static final int STORM_IDLE = ObjectID.WINT_SNOW_STORM_IDLE;

	// The Doors of Dinh between the camp and the arena, which glow green
	static final int DOOR = ObjectID.WINT_DOOR;
	static final int DOOR_FRAME = ObjectID.WINT_DOOR_FRAME;

	// Roots to pick bruma roots and bruma herbs from, and the roots growing out of the walls
	static final Set<Integer> SUPPLY_ROOTS = ImmutableSet.of(
		ObjectID.WINT_ROOTS, ObjectID.WINT_HERB_ROOTS,
		ObjectID.WINT_WALL_BRUMA_ROOT_A, ObjectID.WINT_WALL_HERB_ROOT_B
	);

	// Supply crates by the entrance, each giving one tool or the unfinished potion
	static final int CRATE_KNIFE = ObjectID.WINT_CHEST_KNIFE;
	static final int CRATE_HAMMER = ObjectID.WINT_CHEST_HAMMER;
	static final int CRATE_AXE = ObjectID.WINT_CHEST_AXE;
	static final int CRATE_TINDERBOX = ObjectID.WINT_CHEST_TINDERBOX;
	static final int CRATE_POTION = ObjectID.WINT_CHEST_VIAL;
	static final int BRUMA_ROOTS = ObjectID.WINT_ROOTS;

	static final int PYROMANCER_DOWN = NpcID.WINT_WIZARD_DOWN;
	// An unnamed NPC: the magic fire sent at the Wintertodt
	static final int FIRE_NPC = NpcID.WINT_FIRE;

	// Snow attacks (ids seen in game 2026-10-09). On players: projectile 1310 landing as graphic
	// 1311. On braziers and Pyromancers: projectile 501 landing as graphic 502 (the skeletal
	// wyvern's ice breath, reused). The snow you actually see falling onto a brazier or Pyromancer
	// is a separate animated object, spawned on a plus shape of tiles (borrowed from Clan Wars).
	static final int SNOW_FALLING = ObjectID.CLANWARS_SNOWFALLING;
	static final Set<Integer> SNOWFALL_PROJECTILES = ImmutableSet.of(
		SpotanimID.WINT_AREA_ATTACK_SPOT,
		SpotanimID.WYVERN_SKELETON_TRAVEL_BREATH
	);
	static final Set<Integer> SNOWFALL_IMPACTS = ImmutableSet.of(
		SpotanimID.WINT_AREA_ATTACK_IMPACT,
		SpotanimID.WYVERN_SKELETON_IMPACT_BREATH
	);

	/**
	 * Objects the atmosphere darkening leaves alone, so fires, braziers and the Wintertodt stay readable.
	 */
	static final Set<Integer> KEEP_BRIGHT = ImmutableSet.of(
		BRAZIER_UNLIT, BRAZIER_BROKEN, BRAZIER_LIT,
		STORM_ACTIVE, STORM_IDLE,
		DOOR, DOOR_FRAME,
		ObjectID.WINT_ROOTS, ObjectID.WINT_HERB_ROOTS,
		ObjectID.WINT_BONFIRE
	);

	private WintertodtIds()
	{
	}

	// The camp and arena regions together, in world tile coordinates (edges, so tile x covers x to x + 1)
	private static final int AREA_WEST = (CAMP_REGION >> 8) * 64;
	private static final int AREA_EAST = AREA_WEST + 64;
	private static final int AREA_SOUTH = (CAMP_REGION & 0xFF) * 64;
	private static final int AREA_NORTH = ((ARENA_REGION & 0xFF) + 1) * 64;
	// How many tiles past the area the atmosphere fades out over, so there's no hard line
	// between the storm and the plain white snow around it
	static final int FADE_TILES = 32;

	/**
	 * Tiles from the camp and arena to a point (0 inside them).
	 */
	static double distanceToArea(double x, double y)
	{
		double dx = Math.max(0, Math.max(AREA_WEST - x, x - AREA_EAST));
		double dy = Math.max(0, Math.max(AREA_SOUTH - y, y - AREA_NORTH));
		return Math.max(dx, dy);
	}

	/**
	 * How strong the atmosphere is at a point: 1 in the camp and arena, fading to 0
	 * {@link #FADE_TILES} tiles out.
	 */
	static double atmosphereStrength(double x, double y)
	{
		return Math.max(0, 1 - distanceToArea(x, y) / FADE_TILES);
	}
}
