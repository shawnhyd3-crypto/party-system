package com.partysystem;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Turns a WorldPoint into a short human label ("Varrock", "near Falador", "Wilderness lvl 12").
 * Landmark coordinates are approximate town centres; the label is a hint, not a survey.
 */
final class LocationNames
{
	private static final int EXACT_RADIUS = 30;
	private static final int NEAR_RADIUS = 75;
	private static final int UNDERGROUND_OFFSET = 6400;

	private static final class Landmark
	{
		final String name;
		final int x;
		final int y;

		Landmark(String name, int x, int y)
		{
			this.name = name;
			this.x = x;
			this.y = y;
		}

		int distance(int px, int py)
		{
			return Math.max(Math.abs(px - x), Math.abs(py - y));
		}
	}

	/** Areas outside the normal surface/dungeon coordinate bands, matched on their real coordinates. */
	private static final List<Landmark> SPECIAL = Arrays.asList(
		new Landmark("Zanaris", 2412, 4434),
		new Landmark("Mor Ul Rek", 2480, 5170),
		new Landmark("God Wars Dungeon", 2880, 5310),
		new Landmark("Motherlode Mine", 3760, 5670),
		new Landmark("Prifddinas", 3264, 6080),
		new Landmark("Keldagrim", 2860, 10190),
		new Landmark("Guardians of the Rift", 3615, 9480)
	);

	private static final List<Landmark> SURFACE = Arrays.asList(
		// Misthalin / Asgarnia
		new Landmark("Lumbridge", 3222, 3218),
		new Landmark("Draynor Village", 3093, 3245),
		new Landmark("Wizards' Tower", 3109, 3162),
		new Landmark("Al Kharid", 3293, 3180),
		new Landmark("Varrock", 3213, 3428),
		new Landmark("Grand Exchange", 3165, 3487),
		new Landmark("Edgeville", 3093, 3493),
		new Landmark("Barbarian Village", 3082, 3420),
		new Landmark("Falador", 2965, 3380),
		new Landmark("Rimmington", 2957, 3214),
		new Landmark("Port Sarim", 3023, 3208),
		new Landmark("Taverley", 2895, 3443),
		new Landmark("Burthorpe", 2898, 3544),
		new Landmark("Emir's Arena", 3365, 3265),
		// Kandarin
		new Landmark("Catherby", 2813, 3447),
		new Landmark("Camelot", 2757, 3478),
		new Landmark("Seers' Village", 2726, 3485),
		new Landmark("East Ardougne", 2662, 3305),
		new Landmark("West Ardougne", 2530, 3300),
		new Landmark("Fishing Guild", 2611, 3393),
		new Landmark("Port Khazard", 2654, 3164),
		new Landmark("Yanille", 2606, 3093),
		new Landmark("Castle Wars", 2442, 3089),
		new Landmark("Gnome Stronghold", 2461, 3444),
		new Landmark("Barbarian Outpost", 2520, 3570),
		new Landmark("Piscatoris", 2337, 3690),
		// Fremennik
		new Landmark("Rellekka", 2660, 3657),
		new Landmark("Miscellania", 2535, 3860),
		new Landmark("Neitiznot", 2335, 3800),
		new Landmark("Jatizso", 2415, 3800),
		new Landmark("Lunar Isle", 2100, 3914),
		// Karamja
		new Landmark("Brimhaven", 2760, 3178),
		new Landmark("Musa Point", 2918, 3176),
		new Landmark("Tai Bwo Wannai", 2789, 3065),
		new Landmark("Shilo Village", 2852, 2955),
		new Landmark("Ape Atoll", 2755, 2784),
		// Desert
		new Landmark("Shantay Pass", 3304, 3117),
		new Landmark("Pollnivneach", 3359, 2970),
		new Landmark("Nardah", 3428, 2892),
		new Landmark("Sophanem", 3300, 2780),
		new Landmark("Menaphos", 3240, 2780),
		// Morytania
		new Landmark("Canifis", 3494, 3483),
		new Landmark("Slayer Tower", 3428, 3537),
		new Landmark("Port Phasmatys", 3687, 3502),
		new Landmark("Mort'ton", 3489, 3288),
		new Landmark("Barrows", 3565, 3289),
		new Landmark("Ver Sinhaza", 3650, 3220),
		new Landmark("Darkmeyer", 3605, 3365),
		new Landmark("Mos Le'Harmless", 3685, 2970),
		new Landmark("Fossil Island", 3724, 3808),
		// Tirannwn
		new Landmark("Lletya", 2341, 3171),
		// Kourend & Kebos
		new Landmark("Kourend Castle", 1630, 3680),
		new Landmark("Hosidius", 1750, 3580),
		new Landmark("Shayzien", 1500, 3620),
		new Landmark("Lovakengj", 1504, 3820),
		new Landmark("Arceuus", 1690, 3750),
		new Landmark("Port Piscarius", 1803, 3750),
		new Landmark("Wintertodt", 1630, 3960),
		new Landmark("Farming Guild", 1249, 3720),
		new Landmark("Mount Karuulm", 1311, 3800),
		new Landmark("Mount Quidamortem", 1235, 3560),
		// Varlamore
		new Landmark("Civitas illa Fortis", 1680, 3130)
	);

	private LocationNames()
	{
	}

	static String describe(WorldPoint p, boolean instanced)
	{
		if (p == null)
		{
			return "Unknown";
		}

		String label = baseLabel(p);
		if (instanced)
		{
			label = "Instance (" + label + ")";
		}
		else if (p.getPlane() > 0)
		{
			label += " (upstairs)";
		}
		return label;
	}

	static int wildernessLevel(WorldPoint p)
	{
		int x = p.getX();
		int y = p.getY();
		if (x < 2944 || x > 3391)
		{
			return 0;
		}
		if (y >= 3520 && y <= 3967)
		{
			return (y - 3520) / 8 + 1;
		}
		if (y >= 9920 && y <= 10367)
		{
			return (y - 9920) / 8 + 1;
		}
		return 0;
	}

	private static String baseLabel(WorldPoint p)
	{
		int x = p.getX();
		int y = p.getY();

		Landmark special = nearest(SPECIAL, x, y, NEAR_RADIUS);
		if (special != null)
		{
			return special.name;
		}

		int wildy = wildernessLevel(p);
		if (wildy > 0)
		{
			return "Wilderness lvl " + wildy + (y >= 9920 ? " (underground)" : "");
		}

		boolean underground = y >= 9000;
		int sy = underground ? y - UNDERGROUND_OFFSET : y;
		Landmark lm = nearest(SURFACE, x, sy, NEAR_RADIUS);
		if (lm == null)
		{
			return underground ? "Underground" : "Unknown area";
		}

		boolean exact = lm.distance(x, sy) <= EXACT_RADIUS;
		if (underground)
		{
			return (exact ? "Under " : "Underground near ") + lm.name;
		}
		return exact ? lm.name : "near " + lm.name;
	}

	private static Landmark nearest(List<Landmark> list, int x, int y, int maxDistance)
	{
		Landmark best = null;
		int bestDist = Integer.MAX_VALUE;
		for (Landmark lm : list)
		{
			int d = lm.distance(x, y);
			if (d < bestDist)
			{
				best = lm;
				bestDist = d;
			}
		}
		return bestDist <= maxDistance ? best : null;
	}
}
