package com.wintertodtqol;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PostItemComposition;
import net.runelite.api.events.PreMapLoad;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.GameEventManager;

@Slf4j
@PluginDescriptor(
	name = "Wintertodt QoL",
	description = "A darker, stormier Wintertodt with custom colors, easy-to-spot snow attacks and cues for healing and braziers",
	tags = {"wintertodt", "firemaking", "minigame", "qol", "recolor", "dark"}
)
public class WintertodtQolPlugin extends Plugin
{
	// Config changes that need the map reloaded to redo the floor, scenery, door and root colors
	private static final Set<String> SCENE_KEYS = ImmutableSet.of("darkenScene", "darkness", "arenaColor",
		"fireColor", "recolorFire", "recolorSupplies", "randomColors");
	// Share of the Wintertodt's faces (its darkest) that take the accent colors
	private static final double ACCENT_SHARE = 0.4;
	// Share of each fire's flame faces (its darkest) that take the fire accent
	private static final double FIRE_ACCENT_SHARE = 0.35;
	// Lowest lightness (of 127) for Wintertodt accent colors, so they stay visible
	private static final int ACCENT_MIN_LIGHTNESS = 45;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Notifier notifier;

	@Inject
	private WintertodtQolConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GameEventManager gameEventManager;

	@Inject
	private SceneDarkener sceneDarkener;

	@Inject
	private AtmosphereOverlay atmosphereOverlay;

	@Inject
	private CueOverlay cueOverlay;

	@Inject
	private PotionOverlay potionOverlay;

	@Inject
	private SupplyRecolor supplyRecolor;

	@Inject
	private WintertodtProgress progress;

	@Inject
	private SkiPoses skiPoses;

	@Inject
	private BreathOverlay breathOverlay;

	@Inject
	private PointsOverlay pointsOverlay;

	// Recolors of things that move or animate, put back when turned off
	private final ColorMemory entityColors = new ColorMemory();
	// Fire colors, set from the config
	private int fireMain;
	private int fireAccent;
	// Per fire model color array: flame faces darker than this take the fire accent
	private final Map<int[], Integer> fireCutoffs = new WeakHashMap<>();
	private IntUnaryOperator snowfallRecolor = IntUnaryOperator.identity();
	private int wintertodtMain;
	private int wintertodtAccent1;
	private int wintertodtAccent2;
	// Per Wintertodt model color array: lightness below [0] gets accent 2, below [1] accent 1
	private final Map<int[], int[]> stormCutoffs = new WeakHashMap<>();

	@Getter(AccessLevel.PACKAGE)
	private boolean inArena;
	// How strong the storm overlay is where you stand: 1 in the camp and arena, fading out around them
	@Getter(AccessLevel.PACKAGE)
	private double atmosphereStrength;

	@Getter(AccessLevel.PACKAGE)
	private final Set<GameObject> brokenBraziers = new HashSet<>();
	private final Set<GameObject> unlitBraziers = new HashSet<>();
	private final Set<GameObject> litBraziers = new HashSet<>();
	// The Wintertodt itself, howling or quiescent
	private final Set<GameObject> storms = new HashSet<>();
	// The Wintertodt is raging (a round is on)
	@Getter(AccessLevel.PACKAGE)
	private boolean stormActive;
	// Whether a round was on last game tick, to spot a round ending
	private boolean roundWasOn;
	// Seconds to the next round when last seen, to notify as it passes the warning time
	private int lastSecondsToRound;
	@Getter(AccessLevel.PACKAGE)
	private final Set<NPC> downedPyromancers = new HashSet<>();
	// The snow falling onto braziers and Pyromancers
	@Getter(AccessLevel.PACKAGE)
	private final Set<GameObject> fallingSnow = new HashSet<>();
	// Supply crates by the entrance, and the bruma roots to chop
	private final Set<GameObject> crates = new HashSet<>();
	private final Set<GameObject> brumaRoots = new HashSet<>();

	// -1 when unknown
	@Getter(AccessLevel.PACKAGE)
	private int warmthPercent = -1;

	// The colors in use: from the config, or this round's random pick in random mode
	@Getter(AccessLevel.PACKAGE)
	private Color wintertodtColor;
	private Color wintertodtAccent1Color;
	@Getter(AccessLevel.PACKAGE)
	private Color wintertodtAccent2Color;
	@Getter(AccessLevel.PACKAGE)
	private Color fireColor;
	private Color fireAccentColor;
	// This round's random colors (Wintertodt main, accent 1, accent 2, fire, fire accent), or null
	private Color[] randomColors;
	private final Random random = new Random();

	@Provides
	WintertodtQolConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WintertodtQolConfig.class);
	}

	@Override
	protected void startUp()
	{
		// The overlays read the colors in use, so have them set before they draw
		chooseColors();
		clientThread.invokeLater(this::updateRecolors);
		overlayManager.add(atmosphereOverlay);
		overlayManager.add(cueOverlay);
		overlayManager.add(potionOverlay);
		overlayManager.add(pointsOverlay);
		overlayManager.add(breathOverlay);
		clientThread.invokeLater(() ->
		{
			updateLocation();
			progress.updateItems();
			// Picks up braziers and NPCs already in the scene
			gameEventManager.simulateGameEvents(this);
			reloadMapIfNearby();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(atmosphereOverlay);
		overlayManager.remove(cueOverlay);
		overlayManager.remove(potionOverlay);
		overlayManager.remove(pointsOverlay);
		overlayManager.remove(breathOverlay);
		clientThread.invokeLater(() ->
		{
			skiPoses.restore();
			entityColors.restoreAll();
			sceneDarkener.restore();
			supplyRecolor.setColor(-1);
			clearTracking();
			reloadMapIfNearby();
		});
	}

	// ---------------------------------------------------------------------------------------
	// Events

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!WintertodtQolConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			// Recolors are redone from the original colors on the next frame
			entityColors.restoreAll();
			updateRecolors();
			if (SCENE_KEYS.contains(event.getKey()))
			{
				reloadMapIfNearby();
			}
		});
	}

	@Subscribe
	public void onPreMapLoad(PreMapLoad event)
	{
		// Runs on the map loader thread. Never let a recolor problem break loading the map.
		try
		{
			sceneDarkener.apply(event.getScene(), config);
		}
		catch (RuntimeException | AssertionError e)
		{
			log.warn("Couldn't recolor the Wintertodt area", e);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.HOPPING)
		{
			// Objects and NPCs get spawned again after a load
			clearTracking();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updateLocation();
		progress.checkIdle(client.getLocalPlayer());
		// Random mode picks new colors between rounds, when reloading the area to recolor the
		// doors and roots doesn't get in the way. The storm going quiet means the round ended.
		boolean roundEnded = roundWasOn && !stormActive
			&& storms.stream().anyMatch(storm -> storm.getId() == WintertodtIds.STORM_IDLE);
		if (roundEnded && config.randomColors())
		{
			randomColors = null;
			entityColors.restoreAll();
			updateRecolors();
			reloadMapIfNearby();
		}
		roundWasOn = stormActive;

		// The countdown between rounds; the game also sends it to players who just left, so
		// only notify while here
		int secondsToRound = progress.getSecondsToNextRound();
		int warning = config.roundStartWarning();
		if (inArena && lastSecondsToRound > warning && secondsToRound <= warning)
		{
			notifier.notify(config.roundStartNotification(),
				warning > 0 ? "The Wintertodt round is about to start" : "The Wintertodt round has started");
		}
		lastSecondsToRound = secondsToRound;
		// Warmth is 0-1000 in this varbit (750 shows as 75%)
		warmthPercent = inArena ? Math.min(100, client.getVarbitValue(VarbitID.WINT_WARMTH) / 10) : -1;
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject object = event.getGameObject();
		switch (object.getId())
		{
			case WintertodtIds.BRAZIER_BROKEN:
				brokenBraziers.add(object);
				break;
			case WintertodtIds.BRAZIER_UNLIT:
				unlitBraziers.add(object);
				break;
			case WintertodtIds.BRAZIER_LIT:
				litBraziers.add(object);
				break;
			case WintertodtIds.SNOW_FALLING:
				fallingSnow.add(object);
				// Recolor as it appears, so even its first frame is in color
				if (config.recolorSnowfall() && inArena)
				{
					entityColors.recolor(modelOf(object), snowfallRecolor);
				}
				break;
			case WintertodtIds.CRATE_KNIFE:
			case WintertodtIds.CRATE_HAMMER:
			case WintertodtIds.CRATE_AXE:
			case WintertodtIds.CRATE_TINDERBOX:
			case WintertodtIds.CRATE_POTION:
				crates.add(object);
				break;
			case WintertodtIds.BRUMA_ROOTS:
				brumaRoots.add(object);
				break;
			case WintertodtIds.STORM_ACTIVE:
				storms.add(object);
				stormActive = true;
				break;
			case WintertodtIds.STORM_IDLE:
				storms.add(object);
				stormActive = false;
				break;
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject object = event.getGameObject();
		brokenBraziers.remove(object);
		unlitBraziers.remove(object);
		litBraziers.remove(object);
		storms.remove(object);
		fallingSnow.remove(object);
		crates.remove(object);
		brumaRoots.remove(object);
		if (object.getId() == WintertodtIds.STORM_ACTIVE)
		{
			stormActive = false;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			progress.updateItems();
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Player local = client.getLocalPlayer();
		if (inArena && event.getActor() == local)
		{
			progress.onLocalAnimation(local);
		}
	}

	@Subscribe
	public void onPostItemComposition(PostItemComposition event)
	{
		supplyRecolor.onItemLoaded(event.getItemComposition());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		updatePyromancer(event.getNpc());
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		// A Pyromancer can change into the "Incapacitated" one and back instead of respawning
		updatePyromancer(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		downedPyromancers.remove(event.getNpc());
	}

	// ---------------------------------------------------------------------------------------
	// Recoloring

	/**
	 * Recolors the fire, snow and Wintertodt every client frame (~50 times a second).
	 *
	 * This runs on the client thread on purpose. Asking an animated object for its model
	 * (getModel) is only allowed there, and some renderers (117 HD) draw on their own thread,
	 * so this can't happen inside a render callback. Animation frames share their color arrays
	 * with the base model, so recoloring the current frame's model sticks.
	 */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		updateSkiing();
		if (!inArena)
		{
			return;
		}

		if (config.recolorSnowfall())
		{
			for (GameObject snow : fallingSnow)
			{
				entityColors.recolor(modelOf(snow), snowfallRecolor);
			}
		}

		for (Projectile projectile : client.getProjectiles())
		{
			boolean snow = WintertodtIds.SNOWFALL_PROJECTILES.contains(projectile.getId());
			if (snow ? config.recolorSnowfall() : config.recolorFire())
			{
				Model model = projectile.getModel();
				entityColors.recolor(model, snow ? snowfallRecolor : fireRecolorFor(model, FireKind.FLAMES_ONLY));
			}
		}

		for (GraphicsObject graphic : client.getTopLevelWorldView().getGraphicsObjects())
		{
			boolean snow = WintertodtIds.SNOWFALL_IMPACTS.contains(graphic.getId());
			if (snow ? config.recolorSnowfall() : config.recolorFire())
			{
				Model model = graphic.getModel();
				entityColors.recolor(model, snow ? snowfallRecolor : fireRecolorFor(model, FireKind.FLAMES_ONLY));
			}
		}

		if (config.recolorFire())
		{
			for (GameObject brazier : litBraziers)
			{
				Model model = modelOf(brazier);
				entityColors.recolor(model, fireRecolorFor(model, FireKind.BRAZIER));
			}
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				if (npc.getId() == WintertodtIds.FIRE_NPC)
				{
					// This NPC is only flames, so all of it takes the colors
					Model model = npc.getModel();
					entityColors.recolor(model, fireRecolorFor(model, FireKind.ALL_FLAMES));
				}
			}
		}

		if (config.tintWintertodt())
		{
			for (GameObject storm : storms)
			{
				Model model = modelOf(storm);
				entityColors.recolor(model, stormRecolorFor(model));
			}
		}
	}

	private static Model modelOf(GameObject object)
	{
		Renderable renderable = object.getRenderable();
		if (renderable == null)
		{
			return null;
		}
		if (renderable instanceof Model)
		{
			return (Model) renderable;
		}

		try
		{
			return renderable.getModel();
		}
		catch (RuntimeException e)
		{
			// The game can fail to build an object's model for a moment while it is being
			// removed (seen with the falling snow); skip it for this frame
			return null;
		}
	}

	private void updateRecolors()
	{
		chooseColors();
		int fire = Hsl.fromColor(fireColor);
		// The snow attacks come from the Wintertodt, so they take its first accent color
		int snowfall = Hsl.fromColor(wintertodtAccent1Color);
		wintertodtMain = Hsl.fromColor(wintertodtColor);
		wintertodtAccent1 = snowfall;
		wintertodtAccent2 = Hsl.fromColor(wintertodtAccent2Color);
		stormCutoffs.clear();
		fireMain = fire;
		fireAccent = Hsl.fromColor(fireAccentColor);
		fireCutoffs.clear();
		// The snow is near-white, so it needs its lightness brought down for any color to show
		snowfallRecolor = hsl -> Hsl.recolorWhite(hsl, snowfall);

		// Objects that don't animate are recolored as the map loads (see SceneDarkener)
		IntUnaryOperator doorRecolor = hsl -> Hsl.isGreen(hsl) ? Hsl.withHueAndSaturation(hsl, fire) : hsl;
		// Only the colored parts of the roots, so snow and stone around them stay
		IntUnaryOperator rootRecolor = hsl -> Hsl.saturation(hsl) >= 2 ? Hsl.withHueAndSaturation(hsl, fire) : hsl;
		ImmutableMap.Builder<Integer, IntUnaryOperator> objectRecolors = ImmutableMap.builder();
		if (config.recolorFire())
		{
			objectRecolors.put(WintertodtIds.DOOR, doorRecolor);
			objectRecolors.put(WintertodtIds.DOOR_FRAME, doorRecolor);
		}
		if (config.recolorSupplies())
		{
			for (int root : WintertodtIds.SUPPLY_ROOTS)
			{
				objectRecolors.put(root, rootRecolor);
			}
		}
		sceneDarkener.setObjectRecolors(objectRecolors.build());

		// Supply items in the inventory, on the ground and in hand
		supplyRecolor.setColor(config.recolorSupplies() ? fire : -1);
	}

	/**
	 * Sets the colors in use: the config's, or in random mode this round's random pick
	 * (picking new ones if there are none yet).
	 */
	private void chooseColors()
	{
		if (!config.randomColors())
		{
			randomColors = null;
			wintertodtColor = config.wintertodtColor();
			wintertodtAccent1Color = config.wintertodtAccent1();
			wintertodtAccent2Color = config.wintertodtAccent2();
			fireColor = config.fireColor();
			fireAccentColor = config.fireAccent();
			return;
		}

		if (randomColors == null)
		{
			randomColors = pickRandomColors();
		}
		wintertodtColor = randomColors[0];
		wintertodtAccent1Color = randomColors[1];
		wintertodtAccent2Color = randomColors[2];
		fireColor = randomColors[3];
		fireAccentColor = randomColors[4];
	}

	/**
	 * Random colors that go together: a soft Wintertodt main color with two strong accents of
	 * clearly different hues, and a bright fire with a deeper accent of another hue.
	 */
	private Color[] pickRandomColors()
	{
		float main = random.nextFloat();
		float accent1 = random.nextFloat();
		// At least a fifth of the color wheel apart, so the accents don't blend together
		float accent2 = (accent1 + 0.2f + random.nextFloat() * 0.6f) % 1f;
		float fire = random.nextFloat();
		float fireAccent = (fire + 0.15f + random.nextFloat() * 0.35f) % 1f;
		return new Color[]{
			Color.getHSBColor(main, 0.3f + random.nextFloat() * 0.3f, 0.95f),
			Color.getHSBColor(accent1, 0.85f + random.nextFloat() * 0.15f, 1f),
			Color.getHSBColor(accent2, 0.85f + random.nextFloat() * 0.15f, 0.95f),
			Color.getHSBColor(fire, 0.8f + random.nextFloat() * 0.2f, 1f),
			Color.getHSBColor(fireAccent, 0.85f + random.nextFloat() * 0.15f, 0.85f),
		};
	}

	/**
	 * Which faces of a fire model are flames.
	 */
	private enum FireKind
	{
		// Only the flame-colored faces (projectiles and impacts: the rest isn't fire)
		FLAMES_ONLY,
		// Every face (the magic fire NPC is nothing but flames)
		ALL_FLAMES,
		// Flame-colored faces, plus the faint green glow layer of a burning brazier
		BRAZIER
	}

	/**
	 * The fire recolor for one model: flames take the fire color, with the darkest of them
	 * (relative to this model) taking the fire accent. A brazier's faint glow layer takes the
	 * accent's hue but stays soft, so it doesn't turn into a bright, hard layer.
	 * The brazier flames at Wintertodt are green, the fire sent at the boss is orange.
	 */
	private IntUnaryOperator fireRecolorFor(Model model, FireKind kind)
	{
		IntPredicate isFlame = kind == FireKind.ALL_FLAMES ? hsl -> true : hsl -> Hsl.isFire(hsl) || Hsl.isGreen(hsl);
		int cutoff = model == null ? 0
			: fireCutoffs.computeIfAbsent(model.getFaceColors1(), colors -> darkCutoff(entityColors.originalOf(colors), isFlame));
		return hsl ->
		{
			if (isFlame.test(hsl))
			{
				return Hsl.withHueAndSaturation(hsl, Hsl.lightness(hsl) < cutoff ? fireAccent : fireMain);
			}
			return kind == FireKind.BRAZIER && Hsl.isTinted(hsl) ? Hsl.tintGlow(hsl, fireAccent) : hsl;
		};
	}

	/**
	 * The lightness below which a face is among the darkest {@link #FIRE_ACCENT_SHARE} of the
	 * faces that match.
	 */
	private static int darkCutoff(int[] colors, IntPredicate matches)
	{
		int[] lightness = Arrays.stream(colors).filter(Hsl::isColor).filter(matches).map(Hsl::lightness).sorted().toArray();
		if (lightness.length == 0)
		{
			return 0;
		}

		int index = (int) Math.round(lightness.length * FIRE_ACCENT_SHARE);
		int cutoff = index >= lightness.length ? 128 : lightness[index];
		// Ties: make sure something is split off
		return Math.max(cutoff, lightness[0] + 1);
	}

	/**
	 * The Wintertodt recolor for one model: mostly the main color, with accents on its darkest
	 * faces. Which faces count as dark is relative to this model (its darkest 40%), because the
	 * Wintertodt is almost all bright snow. The darkest half of those get accent 2, the rest
	 * accent 1. Accents are lifted a little so they stay visible.
	 */
	private IntUnaryOperator stormRecolorFor(Model model)
	{
		int[] cutoffs = model == null ? new int[]{0, 0}
			: stormCutoffs.computeIfAbsent(model.getFaceColors1(), colors -> lightnessCutoffs(entityColors.originalOf(colors)));
		return hsl ->
		{
			int lightness = Hsl.lightness(hsl);
			if (lightness >= cutoffs[1])
			{
				return Hsl.withHueAndSaturation(hsl, wintertodtMain);
			}
			int accent = lightness < cutoffs[0] ? wintertodtAccent2 : wintertodtAccent1;
			int lifted = Hsl.pack(Hsl.hue(hsl), Hsl.saturation(hsl), Math.max(lightness, ACCENT_MIN_LIGHTNESS));
			return Hsl.withHueAndSaturation(lifted, accent);
		};
	}

	/**
	 * Lightness values splitting off the darkest faces: [0] the darkest half of the accent
	 * share, [1] the whole accent share.
	 */
	private static int[] lightnessCutoffs(int[] colors)
	{
		int[] lightness = Arrays.stream(colors).filter(Hsl::isColor).map(Hsl::lightness).sorted().toArray();
		if (lightness.length == 0)
		{
			return new int[]{0, 0};
		}

		int accentEnd = (int) Math.round(lightness.length * ACCENT_SHARE);
		int[] cutoffs = new int[]{
			accentEnd / 2 >= lightness.length ? 128 : lightness[accentEnd / 2],
			accentEnd >= lightness.length ? 128 : lightness[accentEnd]
		};
		// Ties: make sure the cutoffs actually split something off
		cutoffs[0] = Math.max(cutoffs[0], lightness[0] + 1);
		cutoffs[1] = Math.max(cutoffs[1], cutoffs[0] + 1);
		return cutoffs;
	}

	// ---------------------------------------------------------------------------------------
	// State

	/**
	 * A round is on while the Wintertodt is howling, or while any brazier is burning.
	 */
	boolean isRoundActive()
	{
		return stormActive || !litBraziers.isEmpty();
	}

	/**
	 * Supply crates and roots worth going to, each with a label: crates for tools you don't
	 * have, the potion crate if you have no potions, and the roots during a round if you have
	 * nothing to burn.
	 */
	Map<GameObject, String> getMissingSupplies()
	{
		Map<GameObject, String> missing = new HashMap<>();
		for (GameObject crate : crates)
		{
			switch (crate.getId())
			{
				case WintertodtIds.CRATE_KNIFE:
					if (!progress.isHasKnife())
					{
						missing.put(crate, "Knife");
					}
					break;
				case WintertodtIds.CRATE_HAMMER:
					if (!progress.isHasHammer())
					{
						missing.put(crate, "Hammer");
					}
					break;
				case WintertodtIds.CRATE_AXE:
					if (!progress.isHasAxe())
					{
						missing.put(crate, "Axe");
					}
					break;
				case WintertodtIds.CRATE_TINDERBOX:
					if (!progress.isHasFireLighter())
					{
						missing.put(crate, "Tinderbox");
					}
					break;
				case WintertodtIds.CRATE_POTION:
					if (!progress.isHasPotions())
					{
						missing.put(crate, "Potions");
					}
					break;
			}
		}
		if (isRoundActive() && progress.getRoots() + progress.getKindling() == 0)
		{
			for (GameObject roots : brumaRoots)
			{
				missing.put(roots, "Roots");
			}
		}
		return missing;
	}

	Set<GameObject> getBraziersNeedingLight()
	{
		return isRoundActive() ? unlitBraziers : Collections.emptySet();
	}

	/**
	 * Skiing poses around the Wintertodt when turned on; your own everywhere else.
	 */
	private void updateSkiing()
	{
		Player local = client.getLocalPlayer();
		if (config.skiing() && local != null && atmosphereStrength > 0)
		{
			skiPoses.apply(local);
		}
		else
		{
			skiPoses.restore();
		}
	}

	private void updateLocation()
	{
		Player local = client.getLocalPlayer();
		int region = local == null ? -1 : local.getWorldLocation().getRegionID();
		inArena = region == WintertodtIds.ARENA_REGION;
		WorldPoint where = local == null ? null : local.getWorldLocation();
		atmosphereStrength = where == null ? 0 : WintertodtIds.atmosphereStrength(where.getX() + 0.5, where.getY() + 0.5);
	}

	private void updatePyromancer(NPC npc)
	{
		if (npc.getId() == WintertodtIds.PYROMANCER_DOWN)
		{
			downedPyromancers.add(npc);
		}
		else
		{
			downedPyromancers.remove(npc);
		}
	}

	private void clearTracking()
	{
		brokenBraziers.clear();
		unlitBraziers.clear();
		litBraziers.clear();
		storms.clear();
		downedPyromancers.clear();
		fallingSnow.clear();
		crates.clear();
		brumaRoots.clear();
		stormActive = false;
	}

	/**
	 * Reloads the map if the player is in or next to the Wintertodt area, so floor and scenery
	 * colors are redone. This is the same trick Dark Wintertodt uses.
	 */
	private void reloadMapIfNearby()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Player local = client.getLocalPlayer();
		// Anywhere the loaded map (104 tiles across) could include the area or its fade
		WorldPoint where = local == null ? null : local.getWorldLocation();
		if (where != null && WintertodtIds.distanceToArea(where.getX(), where.getY()) < 64 + WintertodtIds.FADE_TILES)
		{
			client.setGameState(GameState.LOADING);
		}
	}
}
