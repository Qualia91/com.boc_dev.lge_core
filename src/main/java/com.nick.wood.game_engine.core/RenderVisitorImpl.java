package com.nick.wood.game_engine.core;

import com.nick.wood.game_engine.event_bus.busses.GameBus;
import com.nick.wood.game_engine.gcs_model.gcs.Component;
import com.nick.wood.game_engine.gcs_model.gcs.RenderVisitor;
import com.nick.wood.game_engine.gcs_model.generated.components.*;
import com.nick.wood.graphics_library.communication.*;
import com.nick.wood.graphics_library.objects.lighting.*;
import com.nick.wood.graphics_library.objects.Camera;
import com.nick.wood.graphics_library.objects.CameraType;
import com.nick.wood.graphics_library.objects.materials.BasicMaterial;
import com.nick.wood.graphics_library.objects.materials.Material;
import com.nick.wood.graphics_library.objects.mesh_objects.Model;
import com.nick.wood.graphics_library.objects.render_scene.InstanceObject;
import com.nick.wood.maths.objects.matrix.Matrix4f;
import com.nick.wood.maths.objects.vector.Vec3f;

import java.util.*;

public class RenderVisitorImpl implements RenderVisitor {

	private final GameBus gameBus;
	private final TreeUtils treeUtils;
	private MaterialBuilder materialBuilder;


	private final HashMap<String, HashSet<GeometryObject>> geometryCreateEventsMap = new HashMap<>();
	private final HashMap<String, HashSet<InstanceObject>> geometryUpdateEventsMap = new HashMap<>();
	private final HashMap<String, HashSet<UUID>> geometryDeleteEventsMap = new HashMap<>();


	public RenderVisitorImpl(GameBus gameBus) {
		this.gameBus = gameBus;
		this.treeUtils = new TreeUtils();
		this.materialBuilder = new MaterialBuilder();
	}

	public void send() {

		// do delete first so it only deletes objects already in the scene
		for (Map.Entry<String, HashSet<UUID>> stringArrayListEntry : geometryDeleteEventsMap.entrySet()) {

			gameBus.dispatch(new GeometryRemoveEvent(
					stringArrayListEntry.getValue(),
					stringArrayListEntry.getKey(),
					"MAIN"
			));

		}

		geometryDeleteEventsMap.clear();

		for (Map.Entry<String, HashSet<GeometryObject>> stringGeometryObjectEntry : geometryCreateEventsMap.entrySet()) {

			if (!stringGeometryObjectEntry.getValue().isEmpty()) {

				ArrayList<InstanceObject> instanceObjects = new ArrayList<>(stringGeometryObjectEntry.getValue().size());

				UUID material = null;
				String modelFile = null;

				for (GeometryObject geometryObject : stringGeometryObjectEntry.getValue()) {
					material = geometryObject.getMaterial();
					modelFile = geometryObject.getModelFile();
					instanceObjects.add(new InstanceObject(geometryObject.getUuid(), geometryObject.getGlobalTransform().transpose()));

				}

				gameBus.dispatch(new GeometryCreateEvent(
						instanceObjects,
						new Model(modelFile, material),
						"MAIN"
				));

			}
		}

		geometryCreateEventsMap.clear();

		for (Map.Entry<String, HashSet<InstanceObject>> stringArrayListEntry : geometryUpdateEventsMap.entrySet()) {

			gameBus.dispatch(new GeometryUpdateEvent(
					stringArrayListEntry.getKey(),
					stringArrayListEntry.getValue(),
					"MAIN"
			));

		}

		geometryUpdateEventsMap.clear();
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

		String modelStringId = geometryObject.getModelFile() + geometryObject.getMaterial().toString();

		if (geometryCreateEventsMap.containsKey(modelStringId)) {
			geometryCreateEventsMap.get(modelStringId).add(geometryObject);
		} else {
			HashSet<GeometryObject> instances = new HashSet<>();
			instances.add(geometryObject);
			geometryCreateEventsMap.put(modelStringId, instances);
		}

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
		resolveTransforms(lightObject);

		Light light;

		switch (lightObject.getLightingType()) {

			case POINT:
				light = new PointLight(
						lightObject.getUuid(),
						lightObject.getColour(),
						lightObject.getIntensity(),
						new Attenuation(lightObject.getAttenuationConstant(), lightObject.getAttenuationLinear(), lightObject.getAttenuationExponent())
				);
				break;
			case SPOT:
				PointLight pointLight = new PointLight(
						lightObject.getUuid(),
						lightObject.getColour(),
						lightObject.getIntensity(),
						new Attenuation(lightObject.getAttenuationConstant(), lightObject.getAttenuationLinear(), lightObject.getAttenuationExponent())
				);
				light = new SpotLight(
						lightObject.getUuid(),
						pointLight,
						lightObject.getDirection(),
						lightObject.getConeAngle()
				);
				break;
			default:
				light = new DirectionalLight(
						lightObject.getUuid(),
						lightObject.getColour(),
						lightObject.getDirection(),
						lightObject.getIntensity()
				);
				break;
		}

		// at this point all transforms for current object should be resolved...
		gameBus.dispatch(new LightCreateEvent(
				new InstanceObject(lightObject.getUuid(), lightObject.getGlobalTransform()),
				light,
				"MAIN"
		));
	}

	@Override
	public void sendCreateUpdate(SkyBoxObject skyBoxObject) {

		UUID materialUUID = UUID.randomUUID();
		Model model;
		Material material = new BasicMaterial(materialUUID, skyBoxObject.getTexture());

		gameBus.dispatch(new TextureCreateEvent(
				skyBoxObject.getTexture()
		));

		gameBus.dispatch(new MaterialCreateEvent(
				materialUUID,
				material,
				"MAIN"
		));

		switch (skyBoxObject.getSkyboxType()) {

			case SPHERE:
				model = new Model("DEFAULT_SPHERE_SKYBOX", materialUUID);
				break;
			case CUBE:
			default:
				model = new Model("DEFAULT_CUBE_SKYBOX", materialUUID);
				break;
		}

		gameBus.dispatch(new SkyboxCreateEvent(
				new InstanceObject(skyBoxObject.getUuid(), Matrix4f.Scale(Vec3f.ONE.scale(skyBoxObject.getDistance()))),
				model,
				"MAIN"
		));
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
	public void sendCreateUpdate(TerrainChunkObject terrainChunkObject) {

		gameBus.dispatch(new HeightMapMeshCreateEvent(
				terrainChunkObject.getName(),
				terrainChunkObject.getGrid(),
				terrainChunkObject.getCellSpace()
		));

//		if (geometryCreateEventsMap.containsKey(terrainChunkObject.getName())) {
//			geometryCreateEventsMap.get(terrainChunkObject.getName()).add(geometryObject);
//		} else {
//			ArrayList<GeometryObject> instances = new ArrayList<>();
//			instances.add(geometryObject);
//			geometryCreateEventsMap.put(modelStringId, instances);
//		}
//
//		gameBus.dispatch(new GeometryCreateEvent(
//				new InstanceObject(terrainChunkObject.getUuid(), Matrix4f.Translation(terrainChunkObject.getOrigin()).transpose()),
//				new Model(terrainChunkObject.getName(), findMaterialUUID(terrainChunkObject)),
//				"MAIN"
//		));
	}

	private UUID findMaterialUUID(Component component) {
		UUID materialUUID = null;
		if (component.getParent() != null && component.getParent().getComponentType().equals(ComponentType.TERRAINGENERATION)) {
			TerrainGenerationObject terrainGenerationObject = (TerrainGenerationObject) component.getParent();
			return terrainGenerationObject.getMaterialID();
		}

		return null;
	}

	@Override
	public void sendInstanceUpdate(GeometryObject geometryObject, Matrix4f newTransform) {

		String modelStringId = geometryObject.getModelFile() + geometryObject.getMaterial().toString();

		if (geometryUpdateEventsMap.containsKey(modelStringId)) {
			geometryUpdateEventsMap.get(modelStringId).add(new InstanceObject(geometryObject.getUuid(), newTransform.transpose()));
		} else {
			HashSet<InstanceObject> instances = new HashSet<>();
			instances.add(new InstanceObject(geometryObject.getUuid(), newTransform.transpose()));
			geometryUpdateEventsMap.put(modelStringId, instances);
		}

//		gameBus.dispatch(new GeometryUpdateEvent(
//				geometryObject.getUuid(),
//				new Model(geometryObject.getModelFile(), geometryObject.getMaterial()),
//				"MAIN",
//				newTransform.transpose()
//		));
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
		gameBus.dispatch(new LightUpdateEvent(
				lightObject.getUuid(),
				"MAIN",
				newTransform
		));
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
	public void sendInstanceUpdate(TerrainChunkObject terrainChunkObject, Matrix4f translation) {

	}

	@Override
	public void sendDeleteUpdate(GeometryObject geometryObject) {

		String modelStringId = geometryObject.getModelFile() + geometryObject.getMaterial().toString();

		if (geometryDeleteEventsMap.containsKey(modelStringId)) {
			geometryDeleteEventsMap.get(modelStringId).add(geometryObject.getUuid());
		} else {
			HashSet<UUID> instances = new HashSet<>();
			instances.add(geometryObject.getUuid());
			geometryDeleteEventsMap.put(modelStringId, instances);
		}

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

	@Override
	public void sendDeleteUpdate(TerrainChunkObject terrainChunkObject) {

		gameBus.dispatch(new HeightMapMeshRemoveEvent(
				terrainChunkObject.getName()
		));

//		gameBus.dispatch(new GeometryRemoveEvent(
//				new InstanceObject(terrainChunkObject.getUuid(), Matrix4f.Translation(terrainChunkObject.getOrigin()).transpose()),
//				new Model(terrainChunkObject.getName(), findMaterialUUID(terrainChunkObject)),
//				"MAIN"
//		));

	}
}
