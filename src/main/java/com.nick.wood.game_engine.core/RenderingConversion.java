package com.nick.wood.game_engine.core;

import com.nick.wood.game_engine.event_bus.busses.GameBus;
import com.nick.wood.game_engine.gcs_model.gcs.Component;
import com.nick.wood.maths.objects.matrix.Matrix4f;

import java.util.UUID;

public class RenderingConversion {

	private final RenderVisitorImpl renderVisitorImpl;

	public RenderingConversion(GameBus gameBus) {

		this.renderVisitorImpl = new RenderVisitorImpl(gameBus);

	}

	public void updateRenderableComponentType(Component component) {

		// delete last one, then add new one. easiest way
		component.deleteRenderable(renderVisitorImpl);
		component.createRenderable(renderVisitorImpl);
	}

	public void sendComponentInstanceUpdate(Component component, Matrix4f newTransform) {
		component.updateRenderable(renderVisitorImpl, newTransform);
	}

	public void sendComponentDeleteUpdate(Component component) {
		component.deleteRenderable(renderVisitorImpl);
	}

	public void sendComponentCreateUpdate(Component component) {
		component.createRenderable(renderVisitorImpl);
	}
}
