package com.partysystem;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.WorldService;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.party.PartyPlugin;
import net.runelite.client.plugins.party.PartyPluginService;
import net.runelite.client.plugins.party.data.PartyMemberData;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

@Slf4j
@PluginDescriptor(
	name = "Party System",
	description = "See party members' world and location, hop to their world or path to them",
	tags = {"party", "group", "world", "hop", "location", "map", "friends"}
)
@PluginDependency(PartyPlugin.class)
public class PartySystemPlugin extends Plugin
{
	private static final String SHORTEST_PATH_NAMESPACE = "shortestpath";
	private static final String SHORTEST_PATH_PLUGIN_NAME = "Shortest Path";
	private static final int HEARTBEAT_TICKS = 50; // ~30s
	private static final int HOP_MAX_ATTEMPTS = 5;

	private static final Set<WorldType> RISKY_WORLD_TYPES = EnumSet.of(
		WorldType.PVP,
		WorldType.HIGH_RISK,
		WorldType.DEADMAN,
		WorldType.BETA_WORLD,
		WorldType.NOSAVE_MODE,
		WorldType.TOURNAMENT,
		WorldType.FRESH_START_WORLD,
		WorldType.SEASONAL,
		WorldType.LAST_MAN_STANDING,
		WorldType.PVP_ARENA,
		WorldType.QUEST_SPEEDRUNNING
	);

	@Inject
	private Client client;

	@Inject
	private PartySystemConfig config;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PartySystemOverlay overlay;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private WorldMapOverlay worldMapOverlay;

	@Inject
	private WorldService worldService;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private PartyPluginService partyPluginService;

	/** Remote members only, keyed by party member id. Written from the websocket thread. */
	private final Map<Long, MemberLocation> members = new ConcurrentHashMap<>();
	/** Client thread only. */
	private final Map<Long, PartyMapPoint> mapPoints = new HashMap<>();

	private volatile boolean forceSend;
	private int ticksSinceSend;
	private int lastSentWorld = -1;
	private WorldPoint lastSentPoint;

	private net.runelite.api.World quickHopTarget;
	private int hopAttempts;

	@Provides
	PartySystemConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PartySystemConfig.class);
	}

	@Override
	protected void startUp()
	{
		wsClient.registerMessage(PartySystemLocationUpdate.class);
		overlayManager.add(overlay);
		forceSend = true;
	}

	@Override
	protected void shutDown()
	{
		if (partyService.isInParty() && lastSentWorld > 0)
		{
			sendOffline();
		}
		wsClient.unregisterMessage(PartySystemLocationUpdate.class);
		overlayManager.remove(overlay);
		clearMapPoints();
		members.clear();
		quickHopTarget = null;
		lastSentWorld = -1;
		lastSentPoint = null;
	}

	// ---------------------------------------------------------------- state for the overlay

	boolean isInParty()
	{
		return partyService.isInParty();
	}

	/** The member's colour from the core Party plugin, so we match the Party panel and its map icons. */
	Color colorFor(long memberId)
	{
		PartyMemberData data = partyPluginService.getPartyData(memberId);
		if (data != null && data.getColor() != null)
		{
			return data.getColor();
		}
		return MemberLocation.colorFor(memberId);
	}

	int getPartySize()
	{
		return partyService.getMembers().size();
	}

	List<MemberLocation> getSortedMembers()
	{
		List<MemberLocation> list = new ArrayList<>(members.values());
		list.sort(Comparator.comparing(MemberLocation::getName, String.CASE_INSENSITIVE_ORDER));
		return list;
	}

	// ---------------------------------------------------------------- party events

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		members.clear();
		forceSend = true;
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		forceSend = true;
	}

	@Subscribe
	public void onUserSync(UserSync event)
	{
		forceSend = true;
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		members.remove(event.getMemberId());
	}

	@Subscribe
	public void onPartySystemLocationUpdate(PartySystemLocationUpdate msg)
	{
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == msg.getMemberId())
		{
			return;
		}

		String name = msg.getName();
		if (name == null || name.isEmpty())
		{
			PartyMember pm = partyService.getMemberById(msg.getMemberId());
			name = pm != null ? pm.getDisplayName() : "Member";
		}

		WorldPoint point = msg.getWorld() > 0 ? new WorldPoint(msg.getX(), msg.getY(), msg.getPlane()) : null;
		members.put(msg.getMemberId(), new MemberLocation(
			msg.getMemberId(), Text.removeTags(name), msg.getWorld(), point, msg.isInstanced(), System.currentTimeMillis()));
	}

	// ---------------------------------------------------------------- game events

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			forceSend = true;
		}
		else if (state == GameState.LOGIN_SCREEN && partyService.isInParty() && lastSentWorld > 0)
		{
			sendOffline();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (PartySystemConfig.GROUP.equals(event.getGroup()) && !config.showWorldMapIcons())
		{
			clearMapPoints();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		processQuickHop();

		if (!partyService.isInParty())
		{
			if (!mapPoints.isEmpty())
			{
				clearMapPoints();
			}
			return;
		}

		sendLocationIfNeeded();
		updateMapPoints();
	}

	private void sendLocationIfNeeded()
	{
		Player player = client.getLocalPlayer();
		if (player == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		WorldPoint wp = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		int world = client.getWorld();
		ticksSinceSend++;

		boolean changed = world != lastSentWorld || !wp.equals(lastSentPoint);
		boolean due = (changed && ticksSinceSend >= config.updateIntervalTicks()) || ticksSinceSend >= HEARTBEAT_TICKS;
		if (!forceSend && !due)
		{
			return;
		}

		forceSend = false;
		ticksSinceSend = 0;
		lastSentWorld = world;
		lastSentPoint = wp;

		boolean instanced = client.getTopLevelWorldView().isInstance();
		partyService.send(new PartySystemLocationUpdate(player.getName(), world, wp.getX(), wp.getY(), wp.getPlane(), instanced));
	}

	private void sendOffline()
	{
		Player player = client.getLocalPlayer();
		String name = player != null ? player.getName() : null;
		partyService.send(new PartySystemLocationUpdate(name, 0, 0, 0, 0, false));
		lastSentWorld = 0;
		lastSentPoint = null;
	}

	// ---------------------------------------------------------------- world map

	private void updateMapPoints()
	{
		if (!config.showWorldMapIcons())
		{
			return;
		}

		int myWorld = client.getWorld();
		for (MemberLocation m : members.values())
		{
			PartyMapPoint point = mapPoints.get(m.getMemberId());
			if (!m.isOnline() || m.isStale())
			{
				if (point != null)
				{
					worldMapPointManager.remove(point);
					mapPoints.remove(m.getMemberId());
				}
				continue;
			}
			if (point == null)
			{
				point = new PartyMapPoint(m);
				point.update(m, m.getWorld() == myWorld, colorFor(m.getMemberId()));
				mapPoints.put(m.getMemberId(), point);
				worldMapPointManager.add(point);
			}
			else
			{
				point.update(m, m.getWorld() == myWorld, colorFor(m.getMemberId()));
			}
		}

		Iterator<Map.Entry<Long, PartyMapPoint>> it = mapPoints.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<Long, PartyMapPoint> e = it.next();
			if (!members.containsKey(e.getKey()))
			{
				worldMapPointManager.remove(e.getValue());
				it.remove();
			}
		}
	}

	private void clearMapPoints()
	{
		worldMapPointManager.removeIf(p -> p instanceof PartyMapPoint);
		mapPoints.clear();
	}

	/** Party member whose world map icon is under the mouse, if the world map is open. */
	private MemberLocation worldMapMemberAt(Point mouse)
	{
		if (mouse == null || mapPoints.isEmpty())
		{
			return null;
		}
		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (map == null || map.isHidden())
		{
			return null;
		}
		Rectangle mapBounds = map.getBounds();
		if (!mapBounds.contains(mouse.getX(), mouse.getY()))
		{
			return null;
		}

		int half = PartyMapPoint.SIZE / 2;
		Rectangle snapped = new Rectangle(mapBounds);
		snapped.grow(-half, -half);

		for (PartyMapPoint p : mapPoints.values())
		{
			Point drawPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(p.point());
			if (drawPoint == null)
			{
				continue;
			}
			// Mirror WorldMapOverlay's edge snapping so clamped icons are still clickable
			int cx = Math.max(snapped.x, Math.min(drawPoint.getX(), snapped.x + snapped.width));
			int cy = Math.max(snapped.y, Math.min(drawPoint.getY(), snapped.y + snapped.height));
			if (Math.abs(mouse.getX() - cx) <= half && Math.abs(mouse.getY() - cy) <= half)
			{
				return members.get(p.getMemberId());
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- menus

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!partyService.isInParty())
		{
			return;
		}

		Point mouse = client.getMouseCanvasPosition();

		Long overlayMemberId = overlay.memberAt(mouse);
		if (overlayMemberId != null)
		{
			MemberLocation m = members.get(overlayMemberId);
			if (m != null && m.isOnline())
			{
				addPathEntry(m);
				if (m.getWorld() != client.getWorld())
				{
					addHopEntry(m);
				}
			}
			return;
		}

		MemberLocation m = worldMapMemberAt(mouse);
		if (m != null && m.isOnline())
		{
			// On the map: walkable only when you share a world; otherwise offer the hop
			if (m.getWorld() == client.getWorld())
			{
				addPathEntry(m);
			}
			else
			{
				addHopEntry(m);
			}
		}
	}

	private void addHopEntry(MemberLocation m)
	{
		client.getMenu().createMenuEntry(-1)
			.setOption("Hop-to")
			.setTarget(target(m) + ColorUtil.wrapWithColorTag(" (W" + m.getWorld() + ")", Color.YELLOW))
			.setType(MenuAction.RUNELITE)
			.onClick(e -> hopTo(m.getMemberId()));
	}

	private void addPathEntry(MemberLocation m)
	{
		client.getMenu().createMenuEntry(-1)
			.setOption("Shortest-path")
			.setTarget(target(m))
			.setType(MenuAction.RUNELITE)
			.onClick(e -> pathTo(m.getMemberId()));
	}

	private String target(MemberLocation m)
	{
		return ColorUtil.wrapWithColorTag(m.getName(), colorFor(m.getMemberId()));
	}

	// ---------------------------------------------------------------- actions

	private void pathTo(long memberId)
	{
		MemberLocation m = members.get(memberId);
		if (m == null || !m.isOnline())
		{
			chat("That party member is offline.");
			return;
		}
		if (!isShortestPathEnabled())
		{
			chat("Shortest-path needs the Shortest Path plugin. Install it from the Plugin Hub and enable it.");
			return;
		}

		Map<String, Object> data = new HashMap<>();
		data.put("target", m.getPoint());
		eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, "path", data));

		String note = m.getWorld() == client.getWorld() ? "" : " They're on world " + m.getWorld() + ", hop to meet them.";
		chat("Pathing to " + m.getName() + " (" + LocationNames.describe(m.getPoint(), m.isInstanced()) + ")." + note);
	}

	private boolean isShortestPathEnabled()
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (SHORTEST_PATH_PLUGIN_NAME.equals(p.getName()))
			{
				return pluginManager.isPluginEnabled(p);
			}
		}
		return false;
	}

	private void hopTo(long memberId)
	{
		MemberLocation m = members.get(memberId);
		if (m == null || !m.isOnline())
		{
			chat("That party member is offline.");
			return;
		}
		int target = m.getWorld();
		if (target == client.getWorld())
		{
			chat("You're already on world " + target + ".");
			return;
		}

		WorldResult worlds = worldService.getWorlds();
		World world = worlds == null ? null : worlds.findWorld(target);
		if (world == null)
		{
			chat("Couldn't find world " + target + " in the world list. Try again in a moment.");
			return;
		}

		if (!config.allowRiskyHops())
		{
			for (WorldType t : world.getTypes())
			{
				if (RISKY_WORLD_TYPES.contains(t))
				{
					chat("World " + target + " is a " + t.name().toLowerCase().replace('_', ' ')
						+ " world. Turn on 'Allow risky hops' in Party System settings to hop there.");
					return;
				}
			}
		}

		net.runelite.api.World rsWorld = client.createWorld();
		rsWorld.setActivity(world.getActivity());
		rsWorld.setAddress(world.getAddress());
		rsWorld.setId(world.getId());
		rsWorld.setPlayerCount(world.getPlayers());
		rsWorld.setLocation(world.getRegion().getId());
		rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

		if (client.getGameState() == GameState.LOGIN_SCREEN)
		{
			client.changeWorld(rsWorld);
			return;
		}

		chat("Hopping to world " + target + " to join " + m.getName() + "...");
		quickHopTarget = rsWorld;
		hopAttempts = 0;
	}

	/** Same pattern as the core World Hopper: open the switcher, then hop next tick. */
	private void processQuickHop()
	{
		if (quickHopTarget == null)
		{
			return;
		}

		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			client.openWorldHopper();
			if (++hopAttempts >= HOP_MAX_ATTEMPTS)
			{
				chat("Couldn't open the world switcher to hop. Try again out of combat.");
				quickHopTarget = null;
			}
		}
		else
		{
			client.hopToWorld(quickHopTarget);
			quickHopTarget = null;
		}
	}

	private void chat(String message)
	{
		String text = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("[Party System] ")
			.append(ChatColorType.NORMAL)
			.append(message)
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(text)
			.build());
	}
}
