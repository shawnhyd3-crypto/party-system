package com.partysystem;

import java.awt.Color;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/** Last known state of one party member, as received over the party socket. */
@Value
class MemberLocation
{
	private static final long STALE_MILLIS = 90_000;

	long memberId;
	String name;
	int world;
	WorldPoint point;
	boolean instanced;
	long receivedAt;

	boolean isOnline()
	{
		return world > 0 && point != null;
	}

	boolean isStale()
	{
		return System.currentTimeMillis() - receivedAt > STALE_MILLIS;
	}

	Color color()
	{
		return colorFor(memberId);
	}

	static Color colorFor(long memberId)
	{
		double hue = (Long.hashCode(memberId) * 0.618033988749895) % 1.0;
		if (hue < 0)
		{
			hue += 1.0;
		}
		return Color.getHSBColor((float) hue, 0.7f, 1.0f);
	}
}
