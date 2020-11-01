package com.nick.wood.game_engine.core;

import com.nick.wood.game_engine.event_bus.busses.GameBus;
import com.nick.wood.game_engine.gcs_model.gcs.Component;
import com.nick.wood.game_engine.gcs_model.gcs.RenderVisitor;
import com.nick.wood.game_engine.gcs_model.generated.components.*;
import com.nick.wood.graphics_library.communication.*;
import com.nick.wood.graphics_library.objects.Camera;
import com.nick.wood.graphics_library.objects.CameraType;
import com.nick.wood.graphics_library.objects.mesh_objects.Model;
import com.nick.wood.graphics_library.objects.render_scene.InstanceObject;
import com.nick.wood.maths.objects.matrix.Matrix4f;

public class RenderVisitorImpl implements RenderVisitor {

	private final GameBus gameBus;
	private final TreeUtils treeUtils;
	private MaterialBuilder materialBuilder;


	public RenderVisitorImpl(GameBus gameBus) {
		this.gameBus = gameBus;
		this.treeUtils = new TreeUtils();
		this.materialBuilder = new MaterialBuilder();
	}

	private void resolveTransforms(Component component) {

		// if the component is dirty, then the global transform in it is incorrect and we need
		// to step up the tree to find a clean global transform
		if (component.isDirty()) {

			// get the root clean component
			Component rootCleanComponent = treeUtils.getClosestCleanComponent(component);

			// now walk down the tree and calculate global transforms for all dirty
			// because the transformation resolution has already run at this point
			// clean components will be correct. the dirty ones will be new additions
			treeUtils.resolveGlobalTransforms(rootCleanComponent, Matrix4f.Identity);

		}
	}

	public void sendCreateUpdate(GeometryObject geometryObject) {

		resolveTransforms(geometryObject);

		gameBus.dispatch(new GeometryCreateEvent(
				new InstanceObject(geometryObject.getUuid(), geometryObject.getGlobalTransform().transpose()),
				new Model(geometryObject.getModelFile(), geometryObject.getMaterial()),
				"MAIN"
		));
	}

	public void sendCreateUpdate(MaterialObject materialObject) {

		gameBus.dispatch(new MaterialCreateEvent(
				materialObject.getUuid(),
				materialBuilder.build(materialObject),
				"MAIN"
		));
	}

	public void sendCreateUpdate(CameraObject cameraObject) {

		resolveTransforms(cameraObject);

		// at this point all transforms for current object should be resolved...

		gameBus.dispatch(new CameraCreateEvent(
				new InstanceObject(cameraObject.getUuid(), cameraObject.getGlobalTransform()),
				new Camera(
						cameraObject.getName(),
						CameraType.valueOf(cameraObject.getCameraObjectType().toString()),
						cameraObject.getWidth(),
						cameraObject.getHeight(),
						cameraObject.getFov(),
						cameraObject.getNear(),
						cameraObject.getFar()
				),
				"MAIN"
		));
	}

	@Override
	public void sendCreateUpdate(LightObject lightObject) {

	}

	@Override
	public void sendCreateUpdate(SkyBoxObject skyBoxObject) {

	}

	@Override
	public void sendCreateUpdate(TextObject textObject) {

	}

	@Override
	public void sendCreateUpdate(TextureObject textureObject) {
		// when a texture is created, its parent material needs to have a create event sent so the material
		// manager will have an updated version of the material with textures in
		if (textureObject.getParent() != null && textureObject.getParent().getComponentType().equals(ComponentType.MATERIAL)) {
			sendCreateUpdate((MaterialObject) textureObject.getParent());
		}

		// now sent texture to runtime texture manager
		gameBus.dispatch(new TextureCreateEvent(
				textureObject.getPath()
		));

	}

	@Override
	public void sendCreateUpdate(NormalMapObject normalMapObject) {
		// when a texture is created, its parent material needs to have a create event sent so the material
		// manager will have an updated version of the material with textures in
		if (normalMapObject.getParent() != null && normalMapObject.getParent().getComponentType().equals(ComponentType.MATERIAL)) {
			sendCreateUpdate((MaterialObject) normalMapObject.getParent());
		}

		// now sent texture to runtime texture manager
		gameBus.dispatch(new TextureCreateEvent(
				normalMapObject.getPath()
		));
	}

	@Override
	public void sendInstanceUpdate(GeometryObject geometryObject, Matrix4f newTransform) {
		gameBus.dispatch(new GeometryUpdateEvent(
				geometryObject.getUuid(),
				new Model(geometryObject.getModelFile(), geometryObject.getMaterial()),
				"MAIN",
				newTransform.transpose()
		));
	}

	@Override
	public void sendInstanceUpdate(MaterialObject materialObject, Matrix4f newTransform) {

	}

	@Override
	public void sendInstanceUpdate(CameraObject cameraObject, Matrix4f newTransform) {
		gameBus.dispatch(new CameraUpdateEvent(
				cameraObject.getName(),
				"MAIN",
				newTransform
		));
	}

	@Override
	public void sendInstanceUpdate(LightObject lightObject, Matrix4f newTransform) {

	}

	@Override
	public void sendInstanceUpdate(SkyBoxObject skyBoxObject, Matrix4f newTransform) {

	}

	@Override
	public void sendInstanceUpdate(TextObject textObject, Matrix4f newTransform) {

	}

	@Override
	public void sendInstanceUpdate(TextureObject textureObject, Matrix4f newTransform) {

	}

	@Override
	public void sendInstanceUpdate(NormalMapObject normalMapObject, Matrix4f translation) {

	}

	@Override
	public void sendDeleteUpdate(GeometryObject geometryObject) {
		gameBus.dispatch(new GeometryRemoveEvent(
				new InstanceObject(geometryObject.getUuid(), Matrix4f.Identity),
				new Model(geometryObject.getModelFile(), geometryObject.getMaterial()),
				"MAIN"
		));
	}

	@Override
	public void sendDeleteUpdate(MaterialObject materialObject) {

	}

	@Override
	public void sendDeleteUpdate(CameraObject cameraObject) {

	}

	@Override
	public void sendDeleteUpdate(LightObject lightObject) {

	}

	@Override
	public void sendDeleteUpdate(SkyBoxObject skyBoxObject) {

	}

	@Override
	public void sendDeleteUpdate(TextObject textObject) {

	}

	@Override
	public void sendDeleteUpdate(TextureObject textureObject) {

	}

	@Override
	public void sendDeleteUpdate(NormalMapObject normalMapObject) {

	}
}
