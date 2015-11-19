package com.github.venkataraju.zipsearch;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.border.Border;

final class OvalBorder implements Border
{
	private final Color color;
	private final Insets insets;

	OvalBorder(int radius, Color color)
	{
		this.color = color;
		this.insets = new Insets(radius, radius, radius, radius);
	}

	@Override
	public Insets getBorderInsets(Component c)
	{
		return insets;
	}

	@Override
	public boolean isBorderOpaque()
	{
		return true;
	}

	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int w, int h)
	{
		w--;
		h--;

		int radius = insets.bottom;
		g.setColor(color);

		g.drawLine(x, y + h - radius, x, y + radius);
		g.drawArc(x, y, 2 * radius, 2 * radius, 180, -90);
		g.drawLine(x + radius, y, x + w - radius, y);
		g.drawArc(x + w - 2 * radius, y, 2 * radius, 2 * radius, 90, -90);

		g.drawLine(x + w, y + radius, x + w, y + h - radius);
		g.drawArc(x + w - 2 * radius, y + h - 2 * radius, 2 * radius, 2 * radius, 0, -90);
		g.drawLine(x + radius, y + h, x + w - radius, y + h);
		g.drawArc(x, y + h - 2 * radius, 2 * radius, 2 * radius, -90, -90);
	}
}