package com.worldhopper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class WorldHopperPanel extends PluginPanel
{
	private static final String CONFIG_GROUP = "randomworldhopperpp";
	private static final int VISIBLE_ROWS = 16;
	private static final int ROW_HEIGHT = 20;

	private final WorldHopperPlugin plugin;
	private final JPanel cooldownList = new CooldownListPanel();
	private Timer refreshTimer;

	// getScrollableTracksViewportHeight() = false makes this always use its own natural
	// (row count * ROW_HEIGHT) size instead of being stretched to fill the scroll pane's
	// viewport - without this, GridLayout divides whatever height it's given evenly across
	// however many rows exist, so a handful of rows in a tall viewport get stretched huge.
	private static class CooldownListPanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return ROW_HEIGHT;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return ROW_HEIGHT * VISIBLE_ROWS;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	WorldHopperPanel(WorldHopperPlugin plugin, WorldHopperConfig config, ConfigManager configManager)
	{
		this.plugin = plugin;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel header = new JLabel("Worlds on cooldown");
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(Color.WHITE);
		header.setHorizontalAlignment(SwingConstants.LEFT);
		header.setBorder(new EmptyBorder(0, 0, 5, 0));
		header.setAlignmentX(LEFT_ALIGNMENT);

		cooldownList.setLayout(new GridLayout(0, 1, 0, 6));
		cooldownList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cooldownList.setBorder(new EmptyBorder(8, 8, 8, 8));

		JScrollPane scrollPane = new JScrollPane(cooldownList);
		scrollPane.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR));
		scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setAlignmentX(LEFT_ALIGNMENT);

		int fixedHeight = ROW_HEIGHT * VISIBLE_ROWS + 16;
		Dimension scrollSize = new Dimension(PluginPanel.PANEL_WIDTH, fixedHeight);
		scrollPane.setPreferredSize(scrollSize);
		scrollPane.setMinimumSize(scrollSize);
		scrollPane.setMaximumSize(scrollSize);

		JPanel cooldownFieldRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		cooldownFieldRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		cooldownFieldRow.setAlignmentX(LEFT_ALIGNMENT);
		cooldownFieldRow.setBorder(new EmptyBorder(10, 0, 0, 0));

		JLabel cooldownLabel = new JLabel("World Cooldown (min):");
		cooldownLabel.setFont(FontManager.getRunescapeSmallFont());
		cooldownLabel.setForeground(Color.WHITE);
		cooldownLabel.setBorder(new EmptyBorder(0, 0, 0, 6));

		JSpinner cooldownSpinner = new JSpinner(new SpinnerNumberModel(config.cooldownMinutes(), 1, 120, 1));
		cooldownSpinner.setMaximumSize(new Dimension(60, 24));
		cooldownSpinner.setPreferredSize(new Dimension(60, 24));
		cooldownSpinner.addChangeListener(e ->
			configManager.setConfiguration(CONFIG_GROUP, "cooldownMinutes", (Integer) cooldownSpinner.getValue()));

		cooldownFieldRow.add(cooldownLabel);
		cooldownFieldRow.add(cooldownSpinner);

		Color red = new Color(170, 30, 30);
		Color redHover = new Color(200, 40, 40);

		JButton hopButton = new JButton("Hop to random world");
		hopButton.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
		hopButton.setForeground(Color.WHITE);
		hopButton.setBackground(red);
		hopButton.setBorderPainted(false);
		hopButton.setFocusPainted(false);
		hopButton.setOpaque(true);
		hopButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
		hopButton.setAlignmentX(LEFT_ALIGNMENT);
		hopButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
		hopButton.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 44));
		hopButton.setBorder(new EmptyBorder(12, 0, 12, 0));
		hopButton.addActionListener(e -> plugin.hopToRandomWorld());
		hopButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hopButton.setBackground(redHover);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hopButton.setBackground(red);
			}
		});

		JPanel container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);
		container.add(header);
		container.add(scrollPane);
		container.add(cooldownFieldRow);
		container.add(javax.swing.Box.createRigidArea(new Dimension(0, 10)));
		container.add(hopButton);

		add(container, BorderLayout.NORTH);
	}

	@Override
	public void onActivate()
	{
		plugin.setPanelActive(true);
		refresh();
		refreshTimer = new Timer(1000, e -> refresh());
		refreshTimer.start();
	}

	@Override
	public void onDeactivate()
	{
		plugin.setPanelActive(false);
		if (refreshTimer != null)
		{
			refreshTimer.stop();
			refreshTimer = null;
		}
	}

	private void refresh()
	{
		cooldownList.removeAll();

		Map<Integer, Instant> cooldowns = plugin.getWorldCooldowns();
		Instant now = Instant.now();

		// Every cooldown has the same duration at the moment it's added, so the entry with the
		// furthest-off expiry is also the one most recently added - sorting by expiry descending
		// puts the newest first.
		List<Map.Entry<Integer, Instant>> sorted = new ArrayList<>(cooldowns.entrySet());
		sorted.sort(Map.Entry.<Integer, Instant>comparingByValue().reversed());

		boolean any = false;
		for (Map.Entry<Integer, Instant> entry : sorted)
		{
			Duration remaining = Duration.between(now, entry.getValue());
			if (remaining.isNegative())
			{
				continue;
			}

			any = true;
			long minutes = remaining.toMinutes();
			long seconds = remaining.minusMinutes(minutes).getSeconds();
			JLabel label = new JLabel(String.format("World %d - %d:%02d", entry.getKey(), minutes, seconds));
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			cooldownList.add(label);
		}

		if (!any)
		{
			JLabel empty = new JLabel("None");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			cooldownList.add(empty);
		}

		cooldownList.revalidate();
		cooldownList.repaint();
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
	}
}
