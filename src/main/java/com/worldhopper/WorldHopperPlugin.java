package com.worldhopper;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

@Slf4j
@PluginDescriptor(
	name = "Random World Hopper++",
	description = "Hop to a random world with a configurable per-world cooldown so you don't land back on a world you just left",
	tags = {"world", "hopper", "random", "cooldown", "hop"}
)
public class WorldHopperPlugin extends Plugin
{
	private static final Duration HOP_BUTTON_COOLDOWN = Duration.ofSeconds(2);
	// Used only for the "world list fetch failed" retry - a transient, likely network-related
	// hiccup that can plausibly resolve almost immediately, unlike the filter/ping failure modes.
	private static final Duration HOP_BUTTON_RETRY_COOLDOWN = Duration.ofMillis(600);
	private static final int MAX_QUICK_HOP_ATTEMPTS = 3;

	private static final String CONFIG_GROUP = "worldhopper";
	private static final String KEY_WORLD_COOLDOWNS = "worldCooldowns";
	private static final Color TAG_COLOR = new Color(160, 32, 240);
	// Skill Total worlds' minimum total level only comes through as free text in
	// World.getActivity() (e.g. "1250 Skill total") - there's no structured field for it.
	private static final Pattern SKILL_TOTAL_PATTERN = Pattern.compile("(\\d+)");

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private WorldService worldService;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private Gson gson;

	@Inject
	private WorldHopperConfig config;

	@Inject
	private WorldHopperOverlay overlay;

	private final Map<Integer, Instant> worldCooldowns = new HashMap<>();
	private NavigationButton navButton;
	private net.runelite.api.World quickHopTarget;
	private int quickHopAttempts;
	private net.runelite.api.World pendingHopWorld;
	private boolean pendingHopWaitedATick;
	private Instant lastHopButtonPress;
	private Duration lastHopButtonCooldown = Duration.ZERO;

	@Getter
	private int sessionHopCount;

	@Getter
	private boolean panelActive;

	@Override
	protected void startUp()
	{
		loadCooldowns();

		BufferedImage icon = ImageUtil.loadImageResource(WorldHopperPlugin.class, "icon.png");
		WorldHopperPanel panel = new WorldHopperPanel(this, config, configManager);
		navButton = NavigationButton.builder()
			.tooltip("Random World Hopper++")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
	}

	void setPanelActive(boolean panelActive)
	{
		this.panelActive = panelActive;
	}

	Map<Integer, Instant> getWorldCooldowns()
	{
		return worldCooldowns;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		progressPendingHop();
		progressQuickHop();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// "Hops this session" means since they last logged in - zero it the moment they land
		// back on the login screen so the next login starts a fresh count.
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			sessionHopCount = 0;
		}
	}

	private void loadCooldowns()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, KEY_WORLD_COOLDOWNS);
		if (json == null || json.isEmpty())
		{
			return;
		}

		Type type = new TypeToken<Map<Integer, Long>>()
		{
		}.getType();
		Map<Integer, Long> saved = gson.fromJson(json, type);
		if (saved == null)
		{
			return;
		}

		Instant now = Instant.now();
		for (Map.Entry<Integer, Long> entry : saved.entrySet())
		{
			Instant expiry = Instant.ofEpochMilli(entry.getValue());
			if (expiry.isAfter(now))
			{
				worldCooldowns.put(entry.getKey(), expiry);
			}
		}
	}

	private void persistCooldowns()
	{
		Map<Integer, Long> toSave = new HashMap<>();
		for (Map.Entry<Integer, Instant> entry : worldCooldowns.entrySet())
		{
			toSave.put(entry.getKey(), entry.getValue().toEpochMilli());
		}
		configManager.setConfiguration(CONFIG_GROUP, KEY_WORLD_COOLDOWNS, gson.toJson(toSave));
	}

	private void recordCurrentWorldCooldown()
	{
		int world = client.getWorld();
		Instant expiry = Instant.now().plus(Duration.ofMinutes(config.cooldownMinutes()));
		worldCooldowns.put(world, expiry);
		persistCooldowns();
	}

	private boolean isOnCooldown(int worldId, Instant now)
	{
		Instant expiry = worldCooldowns.get(worldId);
		return expiry != null && expiry.isAfter(now);
	}

	/**
	 * Records the current world's cooldown, then finds a random eligible world under the
	 * configured ping threshold and hops to it. The cooldown is recorded unconditionally before
	 * the search even runs, so the current world is marked as "done" regardless of whether a
	 * hop target is actually found afterward. Runs the world fetch/filter/ping scan off the
	 * client thread since pinging can take a couple seconds. Debounced so spam-clicking the
	 * button doesn't queue up a pile of redundant scans/hops.
	 */
	void hopToRandomWorld()
	{
		Instant now = Instant.now();
		if (lastHopButtonPress != null && Duration.between(lastHopButtonPress, now).compareTo(lastHopButtonCooldown) < 0)
		{
			return;
		}

		recordCurrentWorldCooldown();

		lastHopButtonPress = now;
		lastHopButtonCooldown = HOP_BUTTON_COOLDOWN;

		log.info("Random World Hopper++: hop button clicked");
		executor.execute(this::findAndHopToWorld);
	}

	private void findAndHopToWorld()
	{
		try
		{
			findAndHopToWorld0();
		}
		catch (Exception e)
		{
			log.warn("Random World Hopper++: hop scan failed", e);
			sendChatMessage("Something went wrong finding a world - see the client log.");
		}
	}

	private void findAndHopToWorld0()
	{
		log.info("Random World Hopper++: fetching world list");
		WorldResult worldResult = worldService.getWorlds();
		if (worldResult == null)
		{
			log.info("Random World Hopper++: world list was null");
			lastHopButtonCooldown = HOP_BUTTON_RETRY_COOLDOWN;
			sendChatMessage("Unable to fetch the world list, try again shortly.");
			return;
		}

		Instant now = Instant.now();
		List<World> candidates = new ArrayList<>();
		for (World world : worldResult.getWorlds())
		{
			if (!matchesWorldTypeFilters(world) || isOnCooldown(world.getId(), now) || !meetsSkillTotalRequirement(world))
			{
				continue;
			}

			candidates.add(world);
		}

		log.info("Random World Hopper++: {} candidate worlds after filtering", candidates.size());

		if (candidates.isEmpty())
		{
			sendChatMessage("No worlds match your current filters (all are excluded or on cooldown).");
			return;
		}

		Collections.shuffle(candidates);

		for (World world : candidates)
		{
			int ping = Ping.ping(world, true);
			log.info("Random World Hopper++: world {} ping {}ms", world.getId(), ping);
			if (ping >= 0 && ping <= config.pingThreshold())
			{
				sendChatMessage("Hopping to World " + world.getId() + "...");
				sessionHopCount++;
				hopTo(world);
				return;
			}
		}

		sendChatMessage("No worlds under " + config.pingThreshold() + "ms ping match your filters.");
	}

	// Mirrors net.runelite.client.plugins.worldhopper.WorldTypeFilter, which is package-private
	// and so can't be referenced directly from an external plugin.
	private boolean matchesWorldTypeFilters(World world)
	{
		Set<WorldType> types = world.getTypes();

		if (!config.allowF2pWorlds() && !types.contains(WorldType.MEMBERS))
		{
			return false;
		}

		return (config.allowNormalWorlds() && isNormalWorld(types))
			|| (config.allowDeadmanWorlds() && types.contains(WorldType.DEADMAN))
			|| (config.allowSeasonalWorlds() && (types.contains(WorldType.SEASONAL) || types.contains(WorldType.TOURNAMENT)))
			|| (config.allowQuestSpeedrunningWorlds() && types.contains(WorldType.QUEST_SPEEDRUNNING))
			|| (config.allowFreshStartWorlds() && types.contains(WorldType.FRESH_START_WORLD))
			|| (config.allowPvpWorlds() && types.contains(WorldType.PVP))
			|| (config.allowSkillTotalWorlds() && types.contains(WorldType.SKILL_TOTAL))
			|| (config.allowHighRiskWorlds() && types.contains(WorldType.HIGH_RISK) && !types.contains(WorldType.PVP))
			|| (config.allowBountyHunterWorlds() && types.contains(WorldType.BOUNTY));
	}

	// Skill Total worlds require a minimum total level to even log into - without this check the
	// hop can land you on a world you're immediately bounced from. Parses the requirement out of
	// the free-text activity field and checks it against the player's live total level; if
	// parsing fails for any reason (text format changes, etc.) the world is allowed through
	// rather than silently hidden, since a failed parse shouldn't exclude an otherwise-valid world.
	private boolean meetsSkillTotalRequirement(World world)
	{
		if (!world.getTypes().contains(WorldType.SKILL_TOTAL))
		{
			return true;
		}

		String activity = world.getActivity();
		if (activity == null)
		{
			return true;
		}

		Matcher matcher = SKILL_TOTAL_PATTERN.matcher(activity);
		if (!matcher.find())
		{
			return true;
		}

		int required = Integer.parseInt(matcher.group(1));
		return client.getTotalLevel() >= required;
	}

	// Deliberately stricter than RuneLite's own WorldTypeFilter.NORMAL, which also lumps in Skill
	// Total and Last Man Standing worlds. Since those have their own separate checkboxes here,
	// "Normal" should mean only plain worlds (optionally members), not those special modes too.
	private static boolean isNormalWorld(Set<WorldType> types)
	{
		EnumSet<WorldType> ignorable = EnumSet.of(WorldType.MEMBERS);
		EnumSet<WorldType> everythingElse = EnumSet.complementOf(ignorable);
		return Sets.intersection(types, everythingElse).isEmpty();
	}

	private void hopTo(World world)
	{
		log.info("Random World Hopper++: hopping to world {}", world.getId());
		clientThread.invoke(() ->
		{
			try
			{
				net.runelite.api.World rsWorld = client.createWorld();
				rsWorld.setActivity(world.getActivity());
				rsWorld.setAddress(world.getAddress());
				rsWorld.setId(world.getId());
				rsWorld.setPlayerCount(world.getPlayers());
				rsWorld.setLocation(world.getLocation());
				rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

				if (client.getGameState() == GameState.LOGIN_SCREEN)
				{
					// On the login screen there's no world switcher widget to open - this just
					// changes the world directly.
					client.changeWorld(rsWorld);
					return;
				}

				// Held for a tick before the quick-hop actually starts (see
				// progressPendingHop()), so the "Hopping to World..." chat message has a chance
				// to land before the world change tears down and resets the chatbox.
				pendingHopWorld = rsWorld;
				pendingHopWaitedATick = false;
			}
			catch (Exception e)
			{
				log.warn("Random World Hopper++: hop failed", e);
			}
		});
	}

	/**
	 * Waits one full game tick after hopTo() before handing the request off to the quick-hop
	 * widget flow, giving the chat message sent alongside it time to land first.
	 */
	private void progressPendingHop()
	{
		if (pendingHopWorld == null)
		{
			return;
		}

		if (!pendingHopWaitedATick)
		{
			pendingHopWaitedATick = true;
			return;
		}

		quickHopTarget = pendingHopWorld;
		quickHopAttempts = 0;
		pendingHopWorld = null;
	}

	/**
	 * Drives the quick-hop request set up by progressPendingHop(): opens the World Switcher
	 * widget if it isn't already, and hops as soon as it appears. Mirrors WorldHopperPlugin's
	 * own onGameTick logic, since that's the only way hopToWorld() reliably takes effect.
	 */
	private void progressQuickHop()
	{
		if (quickHopTarget == null)
		{
			return;
		}

		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			client.openWorldHopper();

			if (++quickHopAttempts >= MAX_QUICK_HOP_ATTEMPTS)
			{
				log.warn("Random World Hopper++: failed to open the world switcher after {} attempts", quickHopAttempts);
				sendChatMessage("Failed to open the world switcher - try hopping again.");
				quickHopTarget = null;
				quickHopAttempts = 0;
			}

			return;
		}

		log.info("Random World Hopper++: world switcher open, hopping to world {}", quickHopTarget.getId());
		client.hopToWorld(quickHopTarget);
		quickHopTarget = null;
		quickHopAttempts = 0;
	}

	private void sendChatMessage(String message)
	{
		String formatted = new ChatMessageBuilder()
			.append("[")
			.append(TAG_COLOR, "RWH")
			.append("] " + message)
			.build();

		clientThread.invoke(() ->
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(formatted)
				.build()));
	}

	@Provides
	WorldHopperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WorldHopperConfig.class);
	}
}
