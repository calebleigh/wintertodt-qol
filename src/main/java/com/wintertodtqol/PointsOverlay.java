package com.wintertodtqol;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * A points panel like RuneLite's built-in Wintertodt overlay: what you're doing, round points
 * toward the reward, inventory points and kindling, and the time to the next round.
 */
class PointsOverlay extends OverlayPanel
{
	private static final Color GOOD = Color.GREEN;
	private static final Color BAD = Color.RED;

	private final WintertodtQolPlugin plugin;
	private final WintertodtQolConfig config;
	private final WintertodtProgress progress;

	@Inject
	PointsOverlay(WintertodtQolPlugin plugin, WintertodtQolConfig config, WintertodtProgress progress)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.progress = progress;
		setPosition(OverlayPosition.BOTTOM_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.pointsOverlay() || !plugin.isInArena())
		{
			return null;
		}

		WintertodtProgress.Activity activity = progress.getActivity();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(activity.getLabel())
			.color(activity == WintertodtProgress.Activity.IDLE ? BAD : GOOD)
			.build());

		int roundPoints = progress.getRoundPoints();
		if (roundPoints >= 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Points:")
				.right(roundPoints + " / " + WintertodtProgress.REWARD_POINTS)
				.rightColor(roundPoints >= WintertodtProgress.REWARD_POINTS ? GOOD : Color.WHITE)
				.build());
		}

		int inventory = progress.getInventoryPoints();
		String inventoryText = progress.getRoots() > 0
			? inventory + " (" + progress.getPotentialPoints() + ") pts"
			: inventory + " pts";
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Inventory:")
			.right(inventoryText)
			.rightColor(inventory > 0 ? GOOD : BAD)
			.build());

		int kindling = progress.getKindling();
		int roots = progress.getRoots();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Kindling:")
			.right(roots > 0 ? kindling + " (" + (kindling + roots) + ")" : Integer.toString(kindling))
			.rightColor(kindling + roots > 0 ? GOOD : BAD)
			.build());

		int seconds = progress.getSecondsToNextRound();
		if (seconds > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Next round:")
				.right(String.format("%d:%02d", seconds / 60, seconds % 60))
				.build());
		}

		return super.render(graphics);
	}
}
