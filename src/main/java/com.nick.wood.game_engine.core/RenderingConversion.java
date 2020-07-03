package com.nick.wood.game_engine.core;

import com.nick.wood.game_engine.model.game_objects.*;
import com.nick.wood.game_engine.model.object_builders.Builder;
import com.nick.wood.game_engine.model.object_builders.CameraBuilder;
import com.nick.wood.game_engine.model.object_builders.GeometryBuilder;
import com.nick.wood.game_engine.model.object_builders.LightingBuilder;
import com.nick.wood.game_engine.model.types.CameraObjectType;
import com.nick.wood.game_engine.model.types.GameObjectType;
import com.nick.wood.game_engine.model.types.LightingType;
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

import static com.nick.wood.game_engine.model.types.CameraObjectType.PRIMARY;

public class RenderingConversion {

	private final HashMap<String, Camera> cameraMap = new HashMap<>();
	private final HashMap<String, Light> lightMap = new HashMap<>();
	private final HashMap<String, MeshObject> meshMap = new HashMap<>();

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

		Iterator<GameObject> iterator = gameObject.getGameObjectData().getChildren().iterator();

		while (iterator.hasNext()) {

			GameObject child = iterator.next();

			if (child.getGameObjectData().isRenderChildren()) {

				switch (child.getGameObjectData().getType()) {

					case TRANSFORM:
						if (child.getGameObjectData().isDelete()) {
							child.getGameObjectData().delete();
							iterator.remove();
						} else {
							TransformObject transformGameObject = (TransformObject) child;
							createRenderLists(renderGraph, transformGameObject, transformGameObject.getTransformForRender().multiply(transformationSoFar));
						}
						break;
					case LIGHT:
						if (child.getGameObjectData().isDelete()) {
							renderGraph.removeLight(child.getGameObjectData().getUuid());
							child.getGameObjectData().delete();
							iterator.remove();
						} else {
							LightObject lightObject = (LightObject) child;
							if (child.getGameObjectData().isVisible()) {
								Light light = getFromMap(lightMap, lightObject.getLightingBuilder());
								if (renderGraph.getLights().containsKey(light)) {
									renderGraph.getLights().get(light).setTransformation(transformationSoFar);
								} else {
									InstanceObject lightInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
									renderGraph.getLights().put(light, lightInstance);
								}
							}
							createRenderLists(renderGraph, lightObject, transformationSoFar);
						}
						break;
					case MESH:
						if (child.getGameObjectData().isVisible()) {
							if (child.getGameObjectData().isDelete()) {
								renderGraph.removeMesh(child.getGameObjectData().getUuid());
								child.getGameObjectData().delete();
								iterator.remove();
							} else {
								GeometryGameObject geometryGameObject = (GeometryGameObject) child;
								if (child.getGameObjectData().isVisible()) {
									MeshObject meshObject = getFromMap(meshMap, geometryGameObject.getGeometryBuilder());
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
						}
						break;
					case TERRAIN:
						if (child.getGameObjectData().isDelete()) {
							renderGraph.removeTerrain(child.getGameObjectData().getUuid());
							child.getGameObjectData().delete();
							iterator.remove();
						} else {
							TerrainObject meshGameObject = (TerrainObject) child;
							MeshObject meshObject = getFromMap(meshMap, meshGameObject.getGeometryBuilder());
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
						}
						break;
					case WATER:
						if (child.getGameObjectData().isDelete()) {
							renderGraph.removeWater(child.getGameObjectData().getUuid());
							child.getGameObjectData().delete();
							iterator.remove();
						} else {
							WaterObject meshGameObject = (WaterObject) child;
							MeshObject meshObject = getFromMap(meshMap, meshGameObject.getGeometryBuilder());
							if (child.getGameObjectData().isVisible()) {
								if (renderGraph.getWaterMeshes().containsKey(meshObject)) {
									InstanceObject meshInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
									renderGraph.getWaterMeshes().get(meshObject).add(meshInstance);
								} else {
									ArrayList<InstanceObject> geometryBuilders = new ArrayList<>();
									InstanceObject meshInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
									geometryBuilders.add(meshInstance);
									renderGraph.getWaterMeshes().put(meshObject, geometryBuilders);

									// also tell the render graph that a new mesh needs to be created when it gets to the renderer
									renderGraph.getMeshesToBuild().add(meshObject.getMesh());
								}
							}
							createRenderLists(renderGraph, meshGameObject, transformationSoFar);
						}
						break;
					case SKYBOX:
						if (child.getGameObjectData().isDelete()) {
							renderGraph.removeSkybox();
							child.getGameObjectData().delete();
							iterator.remove();
						} else {
							SkyBoxObject skyBoxObject = (SkyBoxObject) child;
							MeshObject meshObject = getFromMap(meshMap, skyBoxObject.getGeometryBuilder());
							if (child.getGameObjectData().isVisible()) {
								renderGraph.setSkybox(meshObject);
							}
							createRenderLists(renderGraph, skyBoxObject, transformationSoFar);
						}
						break;
					case CAMERA:
						if (child.getGameObjectData().isDelete()) {
							renderGraph.removeCamera(child.getGameObjectData().getUuid());
							child.getGameObjectData().delete();
							iterator.remove();
						} else {
							CameraObject cameraObject = (CameraObject) child;
							if (child.getGameObjectData().isVisible()) {
								Camera camera = getFromMap(cameraMap, cameraObject.getCameraBuilder());
								if (renderGraph.getCameras().containsKey(camera)) {
									renderGraph.getCameras().get(camera).setTransformation(transformationSoFar);
								} else {
									InstanceObject cameraInstance = new InstanceObject(child.getGameObjectData().getUuid(), transformationSoFar);
									renderGraph.getCameras().put(camera, cameraInstance);
								}
							}
							createRenderLists(renderGraph, cameraObject, transformationSoFar);
						}
						break;
					default:
						if (child.getGameObjectData().isDelete()) {
							child.getGameObjectData().delete();
							iterator.remove();
						} else {
							createRenderLists(renderGraph, child, transformationSoFar);
						}
						break;

				}
			}
		}
	}

	private <T, U extends Builder> T getFromMap(HashMap<String, T> map, U builder) {
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
