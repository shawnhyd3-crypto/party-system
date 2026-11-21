package com.partysystem;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

/** World map marker for one party member. Bright when on your world, faded when not. */
class PartyMapPoint extends WorldMapPoint
{
	static final int SIZE = 14;

	@Getter
	private final long memberId;
	private Color color;
	private BufferedImage sameWorldImage;
	private BufferedImage otherWorldImage;

	PartyMapPoint(MemberLocation m)
	{
		super(m.getPoint(), null);
		this.memberId = m.getMemberId();
		setSnapToEdge(true);
		setJumpOnClick(true);
	}

	void update(MemberLocation m, boolean sameWorld, Color memberColor)
	{
		if (!memberColor.equals(color))
		{
			color = memberColor;
			sameWorldImage = icon(memberColor, 1.0f, true);
			otherWorldImage = icon(memberColor, 0.45f, false);
		}
		setWorldPoint(m.getPoint());
		setImage(sameWorld ? sameWorldImage : otherWorldImage);
		setName(m.getName());
		setTooltip(m.getName() + " - W" + m.getWorld()
			+ (sameWorld ? "" : " (right-click to hop)")
			+ "\n" + LocationNames.describe(m.getPoint(), m.isInstanced()));
	}

	WorldPoint point()
	{
		return getWorldPoint();
	}

	private static BufferedImage icon(Color color, float alpha, boolean ring)
	{
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		g.setColor(Color.BLACK);
		g.fillOval(0, 0, SIZE, SIZE);
		g.setColor(color);
		g.fillOval(2, 2, SIZE - 4, SIZE - 4);
		if (ring)
		{
			g.setStroke(new BasicStroke(1.5f));
			g.setColor(Color.WHITE);
			g.drawOval(1, 1, SIZE - 3, SIZE - 3);
		}
		g.dispose();
		return img;
	}
}
