package com.worldhopper;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

public class WorldHopperOverlay extends OverlayPanel
{
	private final WorldHopperPlugin plugin;

	@Inject
	private WorldHopperOverlay(WorldHopperPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_RIGHT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isPanelActive())
		{
			return null;
		}

		panelComponent.getChildren().clear();

		panelComponent.getChildren().add(LineComponent.builder()
			.left("World Hops:")
			.right(String.valueOf(plugin.getSessionHopCount()))
			.build());

		return super.render(graphics);
	}
}
