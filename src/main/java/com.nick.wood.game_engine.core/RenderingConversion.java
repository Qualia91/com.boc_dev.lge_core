package com.nick.wood.game_engine.core;

import com.nick.wood.game_engine.model.game_objects.*;
import com.nick.wood.game_engine.model.object_builders.Builder;
import com.nick.wood.game_engine.model.object_builders.CameraBuilder;
import com.nick.wood.game_engine.model.object_builders.GeometryBuilder;
import com.nick.wood.game_engine.model.object_builders.LightingBuilder;
import com.nick.wood.graphics_library.lighting.*;
import com.nick.wood.graphics_library.objects.Camera;
import com.nick.wood.graphics_library.objects.CameraType;
import com.nick.wood.graphics_library.objects.TerrainTextureObject;
import com.nick.wood.graphics_library.objects.mesh_objects.MeshBuilder;
import com.nick.wood.graphics_library.objects.mesh_objects.MeshObject;
import com.nick.wood.graphics_library.objects.mesh_objects.MeshType;
import com.nick.wood.graphics_library.objects.render_scene.InstanceObject;
import com.nick.wood.graphics_library.objects.render_scene.RenderGraph;
import com.nick.wood.maths.objects.matrix.Matrix4f;

import java.util.*;
import java.util.function.Function;

public class RenderingConversion {

	// these are weak so gc can get rid of them when the rendering system has destroyed them
	private final WeakHashMap<String, Camera> cameraMap = new WeakHashMap<>();
	private final WeakHashMap<String, Light> lightMap = new WeakHashMap<>();
	private final WeakHashMap<String, MeshObject> meshMap = new WeakHashMap<>();

	private final HashMap<Class<? extends Builder>, Function<Builder, Object>> builderMap = new HashMap<>();

	public RenderingConversion() {

		builderMap.put(CameraBuilder.class,
				(Builder builder) -> {
					CameraBuilder cameraBuilder = (CameraBuilder) builder;
					switch (cameraBuilder.getCameraObjectType()) {
						case PRIMARY:
							return new Camera(
									cameraBuilder.getName(),
									cameraBuilder.getFov(),
									cameraBuilder.getNear(),
									cameraBuilder.getFar());
						default:
							return new Camera(
									cameraBuilder.getName(),
									CameraType.valueOf(cameraBuilder.getCameraObjectType().toString()),
									cameraBuilder.getWidth(),
									cameraBuilder.getHeight(),
									cameraBuilder.getFov(),
									cameraBuilder.getNear(),
									cameraBuilder.getFar());
					}
				});

		builderMap.put(LightingBuilder.class,
				(Builder builder) -> {
					LightingBuilder lightingBuilder = (LightingBuilder) builder;
					switch (lightingBuilder.getLightingType()) {
						case SPOT: {
							PointLight pointLight = new PointLight(
									lightingBuilder.getColour(),
									lightingBuilder.getIntensity(),
									new Attenuation(lightingBuilder.getAttenuationConstant(),
											lightingBuilder.getAttenuationLinear(),
											lightingBuilder.getAttenuationExponent())
							);
							return new SpotLight(
									pointLight,
									lightingBuilder.getDirection(),
									lightingBuilder.getConeAngle()
							);
						}
						case POINT: {
							return new PointLight(
									lightingBuilder.getColour(),
									lightingBuilder.getIntensity(),
									new Attenuation(lightingBuilder.getAttenuationConstant(),
											lightingBuilder.getAttenuationLinear(),
											lightingBuilder.getAttenuationExponent())
							);
						}
						default: {
							return new DirectionalLight(
									lightingBuilder.getColour(),
									lightingBuilder.getDirection(),
									lightingBuilder.getIntensity()
							);
						}
					}
				});

		builderMap.put(
				GeometryBuilder.class,
				this::buildGeometryIO
		);

	}

	private MeshObject buildGeometryIO(Builder builder) {
		GeometryBuilder geometryBuilder = (GeometryBuilder) builder;
		MeshBuilder meshBuilder = new MeshBuilder();
		meshBuilder.setMeshType(MeshType.valueOf(geometryBuilder.getGeometryType().toString()));
		meshBuilder.setInvertedNormals(geometryBuilder.isInvertedNormals());
		meshBuilder.setTexture(geometryBuilder.getTexture());
		meshBuilder.setTransform(geometryBuilder.getTransformation());
		meshBuilder.setNormalTexture(geometryBuilder.getNormalTexture());
		meshBuilder.setTriangleNumber(geometryBuilder.getTriangleNumber());
		meshBuilder.setModelFile(geometryBuilder.getModelFile());
		meshBuilder.setText(geometryBuilder.getText());
		meshBuilder.setFontFile(geometryBuilder.getFontFile());
		meshBuilder.setRowNumber(geometryBuilder.getRowNum());
		meshBuilder.setColNumber(geometryBuilder.getColNum());
		meshBuilder.setTerrainHeightMap(geometryBuilder.getTerrainHeightMap());
		meshBuilder.setCellSpace(geometryBuilder.getCellSpace());
		meshBuilder.setWaterSquareWidth(geometryBuilder.getWaterSquareWidth());
		meshBuilder.setWaterHeight(geometryBuilder.getWaterHeight());
		meshBuilder.setCellSpace(geometryBuilder.getCellSpace());
		meshBuilder.setTriangleNumber(geometryBuilder.getTriangleNumber());
		meshBuilder.setTextureFboCameraName(geometryBuilder.getFboCameraName());
		for (TerrainTextureGameObject terrainTextureGameObject : ((GeometryBuilder) builder).getTerrainTextureGameObjects()) {
			meshBuilder.addTerrainTextureObject(new TerrainTextureObject(
					terrainTextureGameObject.getHeight(),
					terrainTextureGameObject.getTransitionWidth(),
					terrainTextureGameObject.getTexturePath(),
					terrainTextureGameObject.getNormalPath()
			));
		}
		return meshBuilder.build();
	}

	public void createRenderLists(RenderGraph renderGraph, GameObject gameObject, Matrix4f transformationSoFar) {

		System.out.println(meshMap.size());

		Iterator<GameObject> iterator = gameObject.getGameObjectData().getChildren().iterator();

		while (iterator.hasNext()) {

			GameObject child = iterator.next();

			if (child.getGameObjectData().isDelete()) {
				delete(child, renderGraph);
				iterator.remove();
			}

			if (child.getGameObjectData().isRenderChildren()) {

				switch (child.getGameObjectData().getType()) {

					case TRANSFORM:
						TransformObject transformGameObject = (TransformObject) child;
						createRenderLists(renderGraph, transformGameObject, transformGameObject.getTransformForRender().multiply(transformationSoFar));
						break;
					case LIGHT:
						LightObject lightObject = (LightObject) child;
						if (child.getGameObjectData().isVisible()) {
							Light light = getFromMap(lightMap, lightObject.getBuilder());
							if (renderGraph.getLights().containsKey(light)) {
								renderGraph.getLights().get(light).setTransformation(transformationSoFar);
							} else {
								InstanceObject lightInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
								renderGraph.getLights().put(light, lightInstance);
							}
						}
						createRenderLists(renderGraph, lightObject, transformationSoFar);
						break;
					case MESH:
						if (child.getGameObjectData().isVisible()) {
							GeometryGameObject geometryGameObject = (GeometryGameObject) child;
							if (child.getGameObjectData().isVisible()) {
								MeshObject meshObject = getFromMap(meshMap, geometryGameObject.getBuilder());
								boolean found = false;
								for (Map.Entry<MeshObject, ArrayList<InstanceObject>> geometryBuilderArrayListEntry : renderGraph.getMeshes().entrySet()) {
									if (geometryBuilderArrayListEntry.getKey().getStringToCompare().equals(meshObject.getStringToCompare())) {
										InstanceObject meshInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
										geometryBuilderArrayListEntry.getValue().add(meshInstance);
										found = true;
										break;
									}
								}
								if (!found) {
									ArrayList<InstanceObject> geometryBuilders = new ArrayList<>();
									InstanceObject meshInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
									geometryBuilders.add(meshInstance);
									renderGraph.getMeshes().put(meshObject, geometryBuilders);

									// also tell the render graph that a new mesh needs to be created when it gets to the renderer
									renderGraph.getMeshesToBuild().add(meshObject.getMesh());
								}
							}
							createRenderLists(renderGraph, geometryGameObject, transformationSoFar);
						}
						break;
					case TERRAIN:
						TerrainObject meshGameObject = (TerrainObject) child;
						MeshObject meshObject = getFromMap(meshMap, meshGameObject.getBuilder());
						if (child.getGameObjectData().isVisible()) {
							if (renderGraph.getTerrainMeshes().containsKey(meshObject)) {
								InstanceObject meshInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
								renderGraph.getTerrainMeshes().get(meshObject).add(meshInstance);
							} else {
								ArrayList<InstanceObject> geometryBuilders = new ArrayList<>();
								InstanceObject meshInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
								geometryBuilders.add(meshInstance);
								renderGraph.getTerrainMeshes().put(meshObject, geometryBuilders);

								// also tell the render graph that a new mesh needs to be created when it gets to the renderer
								renderGraph.getMeshesToBuild().add(meshObject.getMesh());
							}
						}
						createRenderLists(renderGraph, meshGameObject, transformationSoFar);
						break;
					case WATER:
						WaterObject waterMeshGameObject = (WaterObject) child;
						MeshObject waterMeshObject = getFromMap(meshMap, waterMeshGameObject.getBuilder());
						if (child.getGameObjectData().isVisible()) {
							if (renderGraph.getWaterMeshes().containsKey(waterMeshObject)) {
								InstanceObject meshInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
								renderGraph.getWaterMeshes().get(waterMeshObject).add(meshInstance);
							} else {
								ArrayList<InstanceObject> geometryBuilders = new ArrayList<>();
								InstanceObject meshInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
								geometryBuilders.add(meshInstance);
								renderGraph.getWaterMeshes().put(waterMeshObject, geometryBuilders);

								// also tell the render graph that a new mesh needs to be created when it gets to the renderer
								renderGraph.getMeshesToBuild().add(waterMeshObject.getMesh());
							}
						}
						createRenderLists(renderGraph, waterMeshGameObject, transformationSoFar);
						break;
					case SKYBOX:
						SkyBoxObject skyBoxObject = (SkyBoxObject) child;
						MeshObject skyBoxMeshObject = getFromMap(meshMap, skyBoxObject.getBuilder());
						if (child.getGameObjectData().isVisible()) {
							renderGraph.setSkybox(skyBoxMeshObject);
						}
						createRenderLists(renderGraph, skyBoxObject, transformationSoFar);
						break;
					case CAMERA:
						CameraObject cameraObject = (CameraObject) child;
						if (child.getGameObjectData().isVisible()) {
							Camera camera = getFromMap(cameraMap, cameraObject.getBuilder());
							if (renderGraph.getCameras().containsKey(camera)) {
								renderGraph.getCameras().get(camera).setTransformation(transformationSoFar);
							} else {
								InstanceObject cameraInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
								renderGraph.getCameras().put(camera, cameraInstance);
							}
						}
						createRenderLists(renderGraph, cameraObject, transformationSoFar);
						break;
					default:
						createRenderLists(renderGraph, child, transformationSoFar);
						break;

				}
			}
		}
	}

	private void delete(GameObject gameObject, RenderGraph renderGraph) {

		switch (gameObject.getGameObjectData().getType()) {
			case MESH:
				meshMap.remove(gameObject.getBuilder().getName());
				renderGraph.removeMesh(gameObject.getGameObjectData().getUuid());
				break;
			case SKYBOX:
				meshMap.remove(gameObject.getBuilder().getName());
				renderGraph.removeSkybox();
				break;
			case WATER:
				meshMap.remove(gameObject.getBuilder().getName());
				renderGraph.removeWater(gameObject.getGameObjectData().getUuid());
				break;
			case TERRAIN:
				meshMap.remove(gameObject.getBuilder().getName());
				renderGraph.removeTerrain(gameObject.getGameObjectData().getUuid());
				break;
			case CAMERA:
				cameraMap.remove(gameObject.getBuilder().getName());
				renderGraph.removeCamera(gameObject.getGameObjectData().getUuid());
				break;
			case LIGHT:
				lightMap.remove(gameObject.getBuilder().getName());
				renderGraph.removeLight(gameObject.getGameObjectData().getUuid());
				break;
		}
		gameObject.getGameObjectData().delete();
		for (GameObject child : gameObject.getGameObjectData().getChildren()) {
			delete(child, renderGraph);
		}
	}

	private <T, U extends Builder> T getFromMap(WeakHashMap<String, T> map, U builder) {
		if (map.containsKey(builder.getName())) {
			// check if the builder has been updated since it was last create. if so, rebuild and enter into map
			if (builder.isUpdated()) {
				T t = (T) builderMap.get(builder.getClass()).apply(builder);
				map.put(builder.getName(), t);
				builder.setUpdated(false);
			}
			return map.get(builder.getName());
		} else {
			T t = (T) builderMap.get(builder.getClass()).apply(builder);
			map.put(builder.getName(), t);
			builder.setUpdated(false);
			return t;
		}
	}


}
