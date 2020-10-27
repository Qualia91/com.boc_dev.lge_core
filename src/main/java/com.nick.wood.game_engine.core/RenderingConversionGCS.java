package com.nick.wood.game_engine.core;

import com.nick.wood.game_engine.event_bus.busses.GameBus;
import com.nick.wood.game_engine.gcs_model.gcs.Component;
import com.nick.wood.graphics_library.communication.RenderInstanceEventType;
import com.nick.wood.graphics_library.communication.RenderInstanceUpdateEvent;
import com.nick.wood.graphics_library.objects.mesh_objects.MeshObject;
import com.nick.wood.graphics_library.objects.render_scene.InstanceObject;
import com.nick.wood.maths.objects.matrix.Matrix4f;

import java.util.HashMap;
import java.util.UUID;

public class RenderingConversionGCS {

	private final HashMap<UUID, String> componentToInstanceComparisonString = new HashMap<>();
	private final GameBus gameBus;

	public RenderingConversionGCS(GameBus gameBus) {
		this.gameBus = gameBus;
	}

	public MeshObject createNewGraphicsObject(Component component) {
		MeshObject meshObject = null;
		switch (component.getComponentType()) {
			case SKYBOX:
				break;
			case LIGHT:
				break;
			case TRANSFORM:
				break;
			case GEOMETRY:
				break;
			case RIGIDBODY:
				break;
			case CAMERA:
				break;
			default:
				System.err.println("Should not be here!!!");
		}
		componentToInstanceComparisonString.put(component.getUuid(), meshObject.getStringToCompare());
		return meshObject;
	}

	public HashMap<UUID, String> getComponentToInstanceComparisonString() {
		return componentToInstanceComparisonString;
	}

	public void updateRenderableComponentType(Component component) {
		// delete old mesh object
		String oldTypeString = componentToInstanceComparisonString.remove(component.getUuid());
		sendComponentDeleteUpdate(oldTypeString, component.getUuid());

		// create new mesh object and send to graphics
		sendComponentTypeUpdate(component);
	}

	public void sendComponentInstanceUpdate(UUID uuid, Matrix4f newTransform) {
		gameBus.dispatch(new RenderInstanceUpdateEvent(
				componentToInstanceComparisonString.get(uuid),
				new InstanceObject(uuid, newTransform),
				RenderInstanceEventType.TRANSFORM_UPDATE
		));
	}

	public void sendComponentDeleteUpdate(String typeString, UUID uuid) {
		gameBus.dispatch(new RenderInstanceUpdateEvent(
				typeString,
				new InstanceObject(uuid, null),
				RenderInstanceEventType.DESTROY
		));
	}

	public void sendComponentDeleteUpdate(UUID uuid) {
		gameBus.dispatch(new RenderInstanceUpdateEvent(
				componentToInstanceComparisonString.get(uuid),
				new InstanceObject(uuid, null),
				RenderInstanceEventType.DESTROY
		));
	}

	public void sendComponentTypeUpdate(Component component) {
		MeshObject newGraphicsObject = createNewGraphicsObject(component);
		gameBus.dispatch(new RenderInstanceUpdateEvent(
				componentToInstanceComparisonString.get(component.getUuid()),
				newGraphicsObject,
				new InstanceObject(component.getUuid(), null),
				RenderInstanceEventType.TYPE_UPDATE
		));
	}

	public void sendComponentCreateUpdate(UUID uuid, MeshObject meshObject, Matrix4f newTransform) {
		gameBus.dispatch(new RenderInstanceUpdateEvent(
				componentToInstanceComparisonString.get(uuid),
				meshObject,
				new InstanceObject(uuid, newTransform),
				RenderInstanceEventType.CREATE
		));
	}
}
