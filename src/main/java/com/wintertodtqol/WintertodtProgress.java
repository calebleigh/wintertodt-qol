package com.wintertodtqol;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * What you have and what you're doing: points, roots and kindling, the tools you carry, and
 * your current activity. Feeds the points panel and the missing supplies highlights.
 *
 * Points work like RuneLite's built-in Wintertodt plugin: a root in the inventory is worth 10
 * points, or 25 once fletched into kindling. Client thread only.
 */
@Singleton
class WintertodtProgress
{
	static final int REWARD_POINTS = 500;

	// Any axe (not pickaxes or battleaxes), with or without a suffix like "(or)" or "(inactive)"
	private static final Pattern AXE = Pattern.compile("(?i)(^|\\s)axe(\\s*\\(.*\\))?$");
	private static final Pattern DIGITS = Pattern.compile("(\\d+)");
	// After this long without an action animation, you count as idle
	private static final long IDLE_AFTER_MS = 3000;

	enum Activity
	{
		IDLE("Idle"),
		WOODCUTTING("Woodcutting"),
		FLETCHING("Fletching"),
		FEEDING("Feeding brazier"),
		LIGHTING("Lighting brazier"),
		FIXING("Fixing brazier");

		@Getter
		private final String label;

		Activity(String label)
		{
			this.label = label;
		}
	}

	private final Client client;

	@Getter
	private int roots;
	@Getter
	private int kindling;
	@Getter
	private boolean hasKnife;
	@Getter
	private boolean hasHammer;
	@Getter
	private boolean hasAxe;
	// A tinderbox, or a bruma torch (which lights braziers too)
	@Getter
	private boolean hasFireLighter;
	// Any rejuvenation potion, unfinished or finished
	@Getter
	private boolean hasPotions;

	@Getter
	private Activity activity = Activity.IDLE;
	private long lastActionMillis;

	@Inject
	WintertodtProgress(Client client)
	{
		this.client = client;
	}

	/**
	 * Points held in the inventory right now.
	 */
	int getInventoryPoints()
	{
		return roots * 10 + kindling * 25;
	}

	/**
	 * Points the inventory is worth once every root is fletched.
	 */
	int getPotentialPoints()
	{
		return (roots + kindling) * 25;
	}

	/**
	 * Points earned this round, read from the Wintertodt status panel, or -1 if it isn't shown.
	 */
	int getRoundPoints()
	{
		Widget widget = client.getWidget(InterfaceID.WintStatus.POINTS);
		if (widget == null || widget.isHidden() || widget.getText() == null)
		{
			return -1;
		}
		Matcher matcher = DIGITS.matcher(widget.getText());
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
	}

	/**
	 * Seconds until the next round starts, or 0 if a round is on.
	 */
	int getSecondsToNextRound()
	{
		// Converted the same way as RuneLite's built-in Wintertodt plugin
		return client.getVarbitValue(VarbitID.WINT_TRANSMIT_RESPAWNDELAY) * 30 / 50;
	}

	/**
	 * Recounts the inventory and worn equipment. Call when either changes, and on startup.
	 */
	void updateItems()
	{
		roots = 0;
		kindling = 0;
		hasKnife = false;
		hasHammer = false;
		hasAxe = false;
		hasFireLighter = false;
		hasPotions = false;
		scan(client.getItemContainer(InventoryID.INV));
		scan(client.getItemContainer(InventoryID.WORN));
	}

	private void scan(ItemContainer container)
	{
		if (container == null)
		{
			return;
		}

		for (Item item : container.getItems())
		{
			int id = item.getId();
			if (id <= 0)
			{
				continue;
			}

			switch (id)
			{
				case ItemID.WINT_BRUMA_ROOT:
					roots++;
					continue;
				case ItemID.WINT_BRUMA_KINDLING:
					kindling++;
					continue;
				case ItemID.WINT_VIAL:
				case ItemID.WINT_POTION1:
				case ItemID.WINT_POTION2:
				case ItemID.WINT_POTION3:
				case ItemID.WINT_POTION4:
					hasPotions = true;
					continue;
			}

			// Tools by name, so every variant counts (imcando hammer, dragon axe, bruma torch...)
			String name = client.getItemDefinition(id).getName();
			if (name == null)
			{
				continue;
			}
			String lower = name.toLowerCase();
			hasKnife |= lower.equals("knife") || lower.equals("fletching knife");
			hasHammer |= lower.contains("hammer");
			hasAxe |= AXE.matcher(name).find();
			hasFireLighter |= lower.equals("tinderbox") || lower.contains("bruma torch");
		}
	}

	/**
	 * Updates the current activity from the player's animation. Call when it changes.
	 */
	void onLocalAnimation(Player local)
	{
		Activity next = activityFor(local.getAnimation());
		if (next != null)
		{
			activity = next;
			lastActionMillis = System.currentTimeMillis();
		}
	}

	/**
	 * Goes back to idle a few seconds after the last action. Call every game tick.
	 */
	void checkIdle(Player local)
	{
		if (activity != Activity.IDLE && local != null && local.getAnimation() == -1
			&& System.currentTimeMillis() - lastActionMillis > IDLE_AFTER_MS)
		{
			activity = Activity.IDLE;
		}
	}

	void reset()
	{
		activity = Activity.IDLE;
	}

	// The same animations RuneLite's built-in Wintertodt plugin watches
	private static Activity activityFor(int animation)
	{
		switch (animation)
		{
			case AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE:
			case AnimationID.HUMAN_WOODCUTTING_IRON_AXE:
			case AnimationID.HUMAN_WOODCUTTING_STEEL_AXE:
			case AnimationID.HUMAN_WOODCUTTING_BLACK_AXE:
			case AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE:
			case AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE:
			case AnimationID.HUMAN_WOODCUTTING_RUNE_AXE:
			case AnimationID.HUMAN_WOODCUTTING_GILDED_AXE:
			case AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE:
			case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL:
			case AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE:
			case AnimationID.HUMAN_WOODCUTTING_3A_AXE:
			case AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE:
			case AnimationID.HUMAN_OPENHEAVYCHEST:
			case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE_NO_INFERNAL:
			case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE:
			case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE:
			case AnimationID.FORESTRY_2H_AXE_CHOPPING_BRONZE:
			case AnimationID.FORESTRY_2H_AXE_CHOPPING_IRON:
			case AnimationID.FORESTRY_2H_AXE_CHOPPING_STEEL:
			case AnimationID.FORESTRY_2H_AXE_CHOPPING_BLACK:
			case AnimationID.FORESTRY_2H_AXE_CHOPPING_MITHRIL:
			case AnimationID.FORESTRY_2H_AXE_CHOPPING_ADAMANT:
			case AnimationID.FORESTRY_2H_AXE_CHOPPING_RUNE:
			case AnimationID.FORESTRY_2H_AXE_CHOPPING_DRAGON:
			case AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL:
			case AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL_INACTIVE:
			case AnimationID.FORESTRY_2H_AXE_CHOPPING_3A:
				return Activity.WOODCUTTING;
			case AnimationID.HUMAN_FLETCHING:
				return Activity.FLETCHING;
			case AnimationID.HUMAN_PICKUPTABLE:
				return Activity.FEEDING;
			case AnimationID.HUMAN_CREATEFIRE:
				return Activity.LIGHTING;
			case AnimationID.HUMAN_POH_BUILD:
			case AnimationID.HUMAN_POH_BUILD_IMCANDO_HAMMER:
				return Activity.FIXING;
			default:
				return null;
		}
	}
}
