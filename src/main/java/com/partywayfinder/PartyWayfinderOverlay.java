package com.partywayfinder;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;

/**
 * Party list overlay. Draws one row per member and remembers each row's bounds so the
 * plugin can attach a Shortest-path menu entry to the row under the mouse.
 */
class PartyWayfinderOverlay extends Overlay
{
	private static final int PADDING = 5;
	private static final int ROW_GAP = 4;
	private static final int DOT = 8;
	private static final int MIN_WIDTH = 150;

	private static final Color TITLE = new Color(255, 152, 31);
	private static final Color SAME_WORLD = new Color(0, 220, 80);
	private static final Color OTHER_WORLD = new Color(255, 214, 0);
	private static final Color OFFLINE = new Color(150, 150, 150);
	private static final Color SUBTEXT = new Color(200, 200, 200);

	private static final class Row
	{
		final Rectangle bounds;
		final long memberId;

		Row(Rectangle bounds, long memberId)
		{
			this.bounds = bounds;
			this.memberId = memberId;
		}
	}

	private final Client client;
	private final PartyWayfinderPlugin plugin;
	private final PartyWayfinderConfig config;

	private volatile List<Row> rows = Collections.emptyList();

	@Inject
	PartyWayfinderOverlay(Client client, PartyWayfinderPlugin plugin, PartyWayfinderConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_LOW);
		setResettable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		List<MemberLocation> members = plugin.getSortedMembers();
		if (!config.showOverlay() || !plugin.isInParty())
		{
			rows = Collections.emptyList();
			return null;
		}

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int lineH = fm.getHeight();
		int myWorld = client.getWorld();
		boolean coords = config.showCoordinates();

		String title = "Party (" + Math.max(plugin.getPartySize(), members.size() + 1) + ")";

		// Measure
		int width = Math.max(MIN_WIDTH, fm.stringWidth(title));
		for (MemberLocation m : members)
		{
			width = Math.max(width, DOT + 4 + fm.stringWidth(m.getName() + "  " + worldLabel(m)));
			width = Math.max(width, DOT + 4 + fm.stringWidth(subLabel(m, false)));
			if (coords && m.isOnline())
			{
				width = Math.max(width, DOT + 4 + fm.stringWidth(coordLabel(m)));
			}
		}
		int linesPerRow = coords ? 3 : 2;
		int rowH = lineH * linesPerRow;
		int height = PADDING * 2 + lineH + (members.isEmpty() ? lineH : members.size() * (rowH + ROW_GAP));
		width += PADDING * 2;

		g.setColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
		g.fillRect(0, 0, width, height);

		int y = PADDING + fm.getAscent();
		drawText(g, title, PADDING, y, TITLE);
		y += lineH;

		if (members.isEmpty())
		{
			drawText(g, "Waiting for party members...", PADDING, y + ROW_GAP, SUBTEXT);
			rows = Collections.emptyList();
			return new Dimension(width, height);
		}

		List<Row> newRows = new ArrayList<>(members.size());
		int rowTop = PADDING + lineH;
		for (MemberLocation m : members)
		{
			rowTop += ROW_GAP / 2;
			int textY = rowTop + fm.getAscent();
			boolean online = m.isOnline() && !m.isStale();
			Color worldColor = !online ? OFFLINE : (m.getWorld() == myWorld ? SAME_WORLD : OTHER_WORLD);

			g.setColor(online ? plugin.colorFor(m.getMemberId()) : OFFLINE);
			g.fillOval(PADDING, textY - DOT, DOT, DOT);

			int textX = PADDING + DOT + 4;
			drawText(g, m.getName(), textX, textY, Color.WHITE);
			String wl = worldLabel(m);
			drawText(g, wl, width - PADDING - fm.stringWidth(wl), textY, worldColor);

			drawText(g, subLabel(m, m.isStale()), textX, textY + lineH, SUBTEXT);
			if (coords && m.isOnline())
			{
				drawText(g, coordLabel(m), textX, textY + lineH * 2, OFFLINE);
			}

			newRows.add(new Row(new Rectangle(0, rowTop - ROW_GAP / 2, width, rowH + ROW_GAP), m.getMemberId()));
			rowTop += rowH + ROW_GAP / 2;
		}
		rows = newRows;

		return new Dimension(width, height);
	}

	/** Member whose row is under the given canvas point, or null. */
	Long memberAt(Point canvasPoint)
	{
		Rectangle overlayBounds = getBounds();
		if (canvasPoint == null || overlayBounds == null || !overlayBounds.contains(canvasPoint.getX(), canvasPoint.getY()))
		{
			return null;
		}
		int rx = canvasPoint.getX() - overlayBounds.x;
		int ry = canvasPoint.getY() - overlayBounds.y;
		for (Row row : rows)
		{
			if (row.bounds.contains(rx, ry))
			{
				return row.memberId;
			}
		}
		return null;
	}

	private static String worldLabel(MemberLocation m)
	{
		return m.getWorld() > 0 ? "W" + m.getWorld() : "Offline";
	}

	private static String subLabel(MemberLocation m, boolean stale)
	{
		if (!m.isOnline())
		{
			return "Logged out";
		}
		String label = LocationNames.describe(m.getPoint(), m.isInstanced());
		return stale ? label + " (stale)" : label;
	}

	private static String coordLabel(MemberLocation m)
	{
		return m.getPoint().getX() + ", " + m.getPoint().getY() + ", " + m.getPoint().getPlane();
	}

	private static void drawText(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}
}
