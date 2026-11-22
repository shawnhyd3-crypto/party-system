package com.partywayfinder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(PartyWayfinderConfig.GROUP)
public interface PartyWayfinderConfig extends Config
{
	String GROUP = "partywayfinder";

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show the party overlay listing each member's world and location",
		position = 1
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCoordinates",
		name = "Show coordinates",
		description = "Show tile coordinates under each member's location name",
		position = 2
	)
	default boolean showCoordinates()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showWorldMapIcons",
		name = "World map icons",
		description = "Show party members on the world map",
		position = 3
	)
	default boolean showWorldMapIcons()
	{
		return true;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "updateIntervalTicks",
		name = "Update interval (ticks)",
		description = "Minimum game ticks between location updates sent to your party while moving",
		position = 4
	)
	default int updateIntervalTicks()
	{
		return 2;
	}
}
