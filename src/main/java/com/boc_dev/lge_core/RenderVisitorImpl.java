package com.boc_dev.lge_core;

import com.boc_dev.event_bus.busses.GameBus;
import com.boc_dev.graphics_library.communication.*;
import com.boc_dev.graphics_library.objects.lighting.*;
import com.boc_dev.lge_model.gcs.Component;
import com.boc_dev.lge_model.gcs.RenderVisitor;
import com.boc_dev.lge_model.generated.components.*;
import com.boc_dev.graphics_library.objects.ProjectionType;
import com.boc_dev.graphics_library.objects.Camera;
import com.boc_dev.graphics_library.objects.CameraType;
import com.boc_dev.graphics_library.objects.materials.BasicMaterial;
import com.boc_dev.graphics_library.objects.materials.Material;
import com.boc_dev.graphics_library.objects.mesh_objects.Model;
import com.boc_dev.graphics_library.objects.render_scene.InstanceObject;
import com.boc_dev.maths.objects.matrix.Matrix4f;
import com.boc_dev.maths.objects.vector.Vec3f;

import java.util.*;

public class RenderVisitorImpl implements RenderVisitor {

	private final GameBus gameBus;
	private final TreeUtils treeUtils;
	private MaterialBuilder materialBuilder;


	private final HashMap<String, HashSet<GeometryObject>> geometryCreateEventsMap = new HashMap<>();
	private final HashMap<String, HashSet<GeometryObject>> pickingCreateEventsMap = new HashMap<>();
	private final HashMap<String, HashSet<TerrainChunkObject>> terrainCreateEventsMap = new HashMap<>();
	private final HashMap<String, HashSet<MeshObject>> meshCreateEventsMap = new HashMap<>();
	private final HashMap<String, HashSet<InstanceObject>> geometryUpdateEventsMap = new HashMap<>();
	private final HashMap<String, HashSet<InstanceObject>> pickingUpdateEventsMap = new HashMap<>();
	private final HashMap<String, HashSet<UUID>> geometryDeleteEventsMap = new HashMap<>();
	private final HashMap<String, HashSet<UUID>> pickingDeleteEventsMap = new HashMap<>();
	private String layerName = "DEFAULT";


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
					layerName
			));

		}

		geometryDeleteEventsMap.clear();

		// do delete first so it only deletes objects already in the scene
		for (Map.Entry<String, HashSet<UUID>> stringArrayListEntry : pickingDeleteEventsMap.entrySet()) {

			gameBus.dispatch(new PickingRemoveEvent(
					stringArrayListEntry.getValue(),
					stringArrayListEntry.getKey(),
					layerName
			));

		}

		pickingDeleteEventsMap.clear();

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
						layerName
				));

			}
		}

		geometryCreateEventsMap.clear();

		for (Map.Entry<String, HashSet<GeometryObject>> stringGeometryObjectEntry : pickingCreateEventsMap.entrySet()) {

			if (!stringGeometryObjectEntry.getValue().isEmpty()) {

				ArrayList<InstanceObject> instanceObjects = new ArrayList<>(stringGeometryObjectEntry.getValue().size());

				String modelFile = null;

				for (GeometryObject geometryObject : stringGeometryObjectEntry.getValue()) {
					modelFile = geometryObject.getModelFile();
					instanceObjects.add(new InstanceObject(geometryObject.getUuid(), geometryObject.getGlobalTransform().transpose()));

				}

				gameBus.dispatch(new PickingCreateEvent(
						instanceObjects,
						modelFile,
						layerName
				));

			}
		}

		pickingCreateEventsMap.clear();

		for (Map.Entry<String, HashSet<TerrainChunkObject>> stringHashSetEntry : terrainCreateEventsMap.entrySet()) {

			if (!stringHashSetEntry.getValue().isEmpty()) {

				ArrayList<InstanceObject> instanceObjects = new ArrayList<>(stringHashSetEntry.getValue().size());

				TerrainChunkObject anyTerrainChunkObject = null;

				for (TerrainChunkObject terrainChunkObject : stringHashSetEntry.getValue()) {
					anyTerrainChunkObject = terrainChunkObject;
					instanceObjects.add(new InstanceObject(terrainChunkObject.getUuid(), Matrix4f.Translation(terrainChunkObject.getOrigin()).transpose()));

				}

				gameBus.dispatch(new GeometryCreateEvent(
						instanceObjects,
						new Model(anyTerrainChunkObject.getName(), anyTerrainChunkObject.getMaterialID()),
						layerName
				));

			}
		}

		terrainCreateEventsMap.clear();

		for (Map.Entry<String, HashSet<MeshObject>> stringHashSetEntry : meshCreateEventsMap.entrySet()) {

			if (!stringHashSetEntry.getValue().isEmpty()) {

				ArrayList<InstanceObject> instanceObjects = new ArrayList<>(stringHashSetEntry.getValue().size());

				MeshObject anyMesh = null;

				for (MeshObject meshObject : stringHashSetEntry.getValue()) {
					anyMesh = meshObject;
					instanceObjects.add(new InstanceObject(meshObject.getUuid(), Matrix4f.Identity));

				}

				gameBus.dispatch(new GeometryCreateEvent(
						instanceObjects,
						new Model(anyMesh.getName(), anyMesh.getMaterialID()),
						layerName
				));

			}
		}

		meshCreateEventsMap.clear();

		for (Map.Entry<String, HashSet<InstanceObject>> stringArrayListEntry : geometryUpdateEventsMap.entrySet()) {

			gameBus.dispatch(new GeometryUpdateEvent(
					stringArrayListEntry.getKey(),
					stringArrayListEntry.getValue(),
					layerName
			));

		}

		geometryUpdateEventsMap.clear();

		for (Map.Entry<String, HashSet<InstanceObject>> stringArrayListEntry : pickingUpdateEventsMap.entrySet()) {

			gameBus.dispatch(new PickingUpdateEvent(
					stringArrayListEntry.getKey(),
					stringArrayListEntry.getValue(),
					layerName
			));

		}

		pickingUpdateEventsMap.clear();
	}

	private void resolveTransforms(Component component) {

		// if the component is dirty, then the global transform in it is incorrect and we need
		// to step up the tree to find a clean global transform
		if (component.isDirty()) {

			// get the root clean component
			Component rootCleanComponent = treeUtils.getClosestCleanComponent(component);

			// check if this component is the root. If it is, starting transform is Identity.
			// if not, starting transform is parents global transform
			Matrix4f startingMatrix = Matrix4f.Identity;
			if (rootCleanComponent.getParent() != null) {
				startingMatrix = rootCleanComponent.getParent().getGlobalTransform();
			}
			// now walk down the tree and calculate global transforms for all dirty
			// because the transformation resolution has already run at this point
			// clean components will be correct. the dirty ones will be new additions
			treeUtils.resolveGlobalTransforms(rootCleanComponent, startingMatrix);

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
				layerName
		));
	}

	public void sendCreateUpdate(CameraObject cameraObject) {

		resolveTransforms(cameraObject);

		// at this point all transforms for current object should be resolved...

		gameBus.dispatch(new CameraCreateEvent(
				new InstanceObject(cameraObject.getUuid(), cameraObject.getGlobalTransform()),
				new Camera(
						cameraObject.getUuid(),
						cameraObject.getName(),
						CameraType.valueOf(cameraObject.getCameraObjectType().toString()),
						ProjectionType.valueOf(cameraObject.getCameraProjectionType().toString()),
						cameraObject.getWidth(),
						cameraObject.getHeight(),
						cameraObject.getFov(),
						cameraObject.getNear(),
						cameraObject.getFar()
				),
				layerName
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
				layerName
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
				layerName
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
				layerName
		));
	}

	@Override
	public void sendCreateUpdate(TextObject textObject) {
//		resolveTransforms(textObject);
//
//		String textObjectId = textObject.getFontFile() + textObject.getText();
//
//		if (textCreateEventsMap.containsKey(modelStringId)) {
//			textCreateEventsMap.get(modelStringId).add(geometryObject);
//		} else {
//			HashSet<GeometryObject> instances = new HashSet<>();
//			instances.add(geometryObject);
//			textCreateEventsMap.put(modelStringId, instances);
//		}
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


		if (terrainCreateEventsMap.containsKey(terrainChunkObject.getName())) {
			terrainCreateEventsMap.get(terrainChunkObject.getName()).add(terrainChunkObject);
		} else {
			HashSet<TerrainChunkObject> instances = new HashSet<>();
			instances.add(terrainChunkObject);
			terrainCreateEventsMap.put(terrainChunkObject.getName(), instances);
		}

	}

	@Override
	public void sendCreateUpdate(WaterChunkObject waterChunkObject) {

		resolveTransforms(waterChunkObject);

		gameBus.dispatch(new WaterCreateEvent(
				waterChunkObject.getUuid(),
				waterChunkObject.getName(),
				waterChunkObject.getGrid(),
				waterChunkObject.getCellSpace(),
				layerName,
				waterChunkObject.getGlobalTransform().transpose()
		));

	}

	@Override
	public void sendCreateUpdate(PickableObject pickableObject) {

		resolveTransforms(pickableObject);

		// get parent geometry
		if (pickableObject.getParent() != null && pickableObject.getParent().getComponentType().equals(ComponentType.GEOMETRY)) {
			GeometryObject geometryObject = (GeometryObject) pickableObject.getParent();

			String modelStringId = geometryObject.getModelFile() + geometryObject.getMaterial().toString();

			if (pickingCreateEventsMap.containsKey(modelStringId)) {
				pickingCreateEventsMap.get(modelStringId).add(geometryObject);
			} else {
				HashSet<GeometryObject> instances = new HashSet<>();
				instances.add(geometryObject);
				pickingCreateEventsMap.put(modelStringId, instances);
			}
		}

	}

	@Override
	public void sendCreateUpdate(MeshObject meshObject) {

		gameBus.dispatch(new ChunkMeshCreateEvent(
				meshObject.getName(),
				meshObject.getVertexPositions()
		));

		if (meshCreateEventsMap.containsKey(meshObject.getName())) {
			meshCreateEventsMap.get(meshObject.getName()).add(meshObject);
		} else {
			HashSet<MeshObject> instances = new HashSet<>();
			instances.add(meshObject);
			meshCreateEventsMap.put(meshObject.getName(), instances);
		}

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

	}

	@Override
	public void sendInstanceUpdate(MaterialObject materialObject, Matrix4f newTransform) {
	}

	@Override
	public void sendInstanceUpdate(CameraObject cameraObject, Matrix4f newTransform) {
		gameBus.dispatch(new CameraUpdateEvent(
				cameraObject.getName(),
				layerName,
				newTransform
		));
	}

	@Override
	public void sendInstanceUpdate(LightObject lightObject, Matrix4f newTransform) {
		gameBus.dispatch(new LightUpdateEvent(
				lightObject.getUuid(),
				layerName,
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
	public void sendInstanceUpdate(WaterChunkObject waterChunkObject, Matrix4f newTransform) {

	}

	@Override
	public void sendInstanceUpdate(PickableObject pickableObject, Matrix4f newTransform) {

		// get parent geometry
		if (pickableObject.getParent() != null && pickableObject.getParent().getComponentType().equals(ComponentType.GEOMETRY)) {
			GeometryObject geometryObject = (GeometryObject) pickableObject.getParent();
			String modelStringId = geometryObject.getModelFile();

			if (pickingUpdateEventsMap.containsKey(modelStringId)) {
				pickingUpdateEventsMap.get(modelStringId).add(new InstanceObject(geometryObject.getUuid(), newTransform.transpose()));
			} else {
				HashSet<InstanceObject> instances = new HashSet<>();
				instances.add(new InstanceObject(geometryObject.getUuid(), newTransform.transpose()));
				pickingUpdateEventsMap.put(modelStringId, instances);
			}
		}
	}

	@Override
	public void sendInstanceUpdate(MeshObject meshObject, Matrix4f newTransform) {

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

		String modelStringId = terrainChunkObject.getName() + terrainChunkObject.getMaterialID().toString();

		if (geometryDeleteEventsMap.containsKey(modelStringId)) {
			geometryDeleteEventsMap.get(modelStringId).add(terrainChunkObject.getUuid());
		} else {
			HashSet<UUID> instances = new HashSet<>();
			instances.add(terrainChunkObject.getUuid());
			geometryDeleteEventsMap.put(modelStringId, instances);
		}

	}

	@Override
	public void sendDeleteUpdate(WaterChunkObject waterChunkObject) {

	}

	@Override
	public void sendDeleteUpdate(PickableObject pickableObject) {

		// get parent geometry
		if (pickableObject.getParent() != null && pickableObject.getParent().getComponentType().equals(ComponentType.GEOMETRY)) {
			GeometryObject geometryObject = (GeometryObject) pickableObject.getParent();
			String modelStringId = geometryObject.getModelFile();

			if (pickingDeleteEventsMap.containsKey(modelStringId)) {
				pickingDeleteEventsMap.get(modelStringId).add(geometryObject.getUuid());
			} else {
				HashSet<UUID> instances = new HashSet<>();
				instances.add(geometryObject.getUuid());
				pickingDeleteEventsMap.put(modelStringId, instances);
			}

		}

	}

	@Override
	public void sendDeleteUpdate(MeshObject meshObject) {
		gameBus.dispatch(new HeightMapMeshRemoveEvent(
				meshObject.getName()
		));

		String modelStringId = meshObject.getName() + meshObject.getMaterialID().toString();

		if (geometryDeleteEventsMap.containsKey(modelStringId)) {
			geometryDeleteEventsMap.get(modelStringId).add(meshObject.getUuid());
		} else {
			HashSet<UUID> instances = new HashSet<>();
			instances.add(meshObject.getUuid());
			geometryDeleteEventsMap.put(modelStringId, instances);
		}
	}

	public void setLayerName(String layerName) {
		this.layerName = layerName;
	}
}
