package com.partywayfinder;

import static org.junit.Assert.assertEquals;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class LocationNamesTest
{
	@Test
	public void townCentre()
	{
		assertEquals("Lumbridge", LocationNames.describe(new WorldPoint(3222, 3218, 0), false));
	}

	@Test
	public void upstairs()
	{
		assertEquals("Lumbridge (upstairs)", LocationNames.describe(new WorldPoint(3208, 3220, 2), false));
	}

	@Test
	public void nearTown()
	{
		assertEquals("near Varrock", LocationNames.describe(new WorldPoint(3260, 3470, 0), false));
	}

	@Test
	public void wilderness()
	{
		assertEquals("Wilderness lvl 1", LocationNames.describe(new WorldPoint(3100, 3523, 0), false));
		assertEquals("Wilderness lvl 21", LocationNames.describe(new WorldPoint(3100, 3680, 0), false));
	}

	@Test
	public void dungeonUnderTown()
	{
		// Edgeville dungeon sits 6400 tiles north of Edgeville
		assertEquals("Under Edgeville", LocationNames.describe(new WorldPoint(3097, 9867, 0), false));
	}

	@Test
	public void instance()
	{
		assertEquals("Instance (Barrows)", LocationNames.describe(new WorldPoint(3565, 3289, 0), true));
	}

	@Test
	public void middleOfNowhere()
	{
		assertEquals("Unknown area", LocationNames.describe(new WorldPoint(1000, 1000, 0), false));
	}
}
