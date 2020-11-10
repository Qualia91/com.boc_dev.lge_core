package com.boc_dev.lge_core;

import com.boc_dev.event_bus.busses.GameBus;
import com.boc_dev.lge_model.gcs.Component;
import com.boc_dev.maths.objects.matrix.Matrix4f;

public class RenderingConversion {

	private final RenderVisitorImpl renderVisitorImpl;

	public RenderingConversion(GameBus gameBus) {

		this.renderVisitorImpl = new RenderVisitorImpl(gameBus);

	}

	public void send() {
		renderVisitorImpl.send();
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

	public void setLayerName(String layerName) {
		renderVisitorImpl.setLayerName(layerName);
	}
}
