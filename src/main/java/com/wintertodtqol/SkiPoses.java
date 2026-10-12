package com.wintertodtqol;

import javax.inject.Singleton;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.kit.KitType;

/**
 * Swaps your standing, walking and running animations for the skiing ones, and shows skis
 * (with poles) in your hands while you aren't busy with something else. Only you see it.
 *
 * The game sets these poses and your look again whenever your appearance changes (like
 * equipping a weapon), so {@link #apply} is called every frame, and it picks up the new ones as
 * the originals to put back. Client thread only.
 */
@Singleton
class SkiPoses
{
	// idle, idle turn left, idle turn right, walk, walk left, walk right, walk backwards, run
	private static final int[] SKI = {
		AnimationID.HUMAN_SKI_IDLE,
		AnimationID.HUMAN_SKI_IDLE,
		AnimationID.HUMAN_SKI_IDLE,
		AnimationID.HUMAN_SKI_WALK,
		AnimationID.HUMAN_SKI_WALK_LEFT,
		AnimationID.HUMAN_SKI_WALK_RIGHT,
		AnimationID.HUMAN_SKI_WALK_BACKWARDS,
		AnimationID.HUMAN_SKI_RUN,
	};
	// The skis are worn in the weapon slot, and take both hands like the real item
	private static final int SKIS = ItemID.OSB9_REWARD_SKIS + PlayerComposition.ITEM_OFFSET;
	private static final int WEAPON = KitType.WEAPON.getIndex();
	private static final int SHIELD = KitType.SHIELD.getIndex();

	// The poses to put back, or null when nothing is swapped
	private int[] original;
	// The weapon and shield the skis are covering, as appearance ids
	private int originalWeapon;
	private int originalShield;
	private Player skier;

	/**
	 * Puts the skiing poses and skis on the player, keeping their own to put back later.
	 */
	void apply(Player player)
	{
		if (player != skier)
		{
			original = null;
			skier = player;
		}

		int[] current = read(player);
		if (current[0] != AnimationID.HUMAN_SKI_IDLE)
		{
			// First time, or the game just set the poses again
			original = current;
			write(player, SKI);
		}

		// Skis while standing or moving; your own weapon back for chopping, fletching and so on
		showSkis(player, player.getAnimation() == -1);
	}

	/**
	 * Puts the player's own poses and weapon back.
	 */
	void restore()
	{
		if (skier != null)
		{
			showSkis(skier, false);
			if (original != null && read(skier)[0] == AnimationID.HUMAN_SKI_IDLE)
			{
				write(skier, original);
			}
		}
		original = null;
		skier = null;
	}

	private void showSkis(Player player, boolean show)
	{
		PlayerComposition look = player.getPlayerComposition();
		if (look == null)
		{
			return;
		}

		int[] equipment = look.getEquipmentIds();
		boolean showing = equipment[WEAPON] == SKIS;
		if (show && !showing)
		{
			originalWeapon = equipment[WEAPON];
			originalShield = equipment[SHIELD];
			equipment[WEAPON] = SKIS;
			equipment[SHIELD] = 0;
			// Makes the client rebuild the player's model with the new equipment
			look.setHash();
		}
		else if (!show && showing)
		{
			equipment[WEAPON] = originalWeapon;
			equipment[SHIELD] = originalShield;
			look.setHash();
		}
	}

	private static int[] read(Player player)
	{
		return new int[]{
			player.getIdlePoseAnimation(),
			player.getIdleRotateLeft(),
			player.getIdleRotateRight(),
			player.getWalkAnimation(),
			player.getWalkRotateLeft(),
			player.getWalkRotateRight(),
			player.getWalkRotate180(),
			player.getRunAnimation(),
		};
	}

	private static void write(Player player, int[] poses)
	{
		player.setIdlePoseAnimation(poses[0]);
		player.setIdleRotateLeft(poses[1]);
		player.setIdleRotateRight(poses[2]);
		player.setWalkAnimation(poses[3]);
		player.setWalkRotateLeft(poses[4]);
		player.setWalkRotateRight(poses[5]);
		player.setWalkRotate180(poses[6]);
		player.setRunAnimation(poses[7]);
	}
}
