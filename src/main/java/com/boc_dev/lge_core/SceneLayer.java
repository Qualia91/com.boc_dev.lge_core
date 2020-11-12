package com.boc_dev.lge_core;

import com.boc_dev.event_bus.busses.GameBus;
import com.boc_dev.lge_model.gcs.Component;
import com.boc_dev.lge_model.gcs.Registry;
import com.boc_dev.lge_model.gcs.RegistryUpdater;
import com.boc_dev.lge_model.systems.GcsSystem;
import com.boc_dev.graphics_library.Shader;
import com.boc_dev.graphics_library.objects.lighting.Fog;
import com.boc_dev.graphics_library.objects.render_scene.Scene;
import com.boc_dev.maths.objects.vector.Vec3f;

import java.util.ArrayList;

public class SceneLayer {

	private final ArrayList<GcsSystem<Component>> gcsSystems;
	private final String layerName;
	private final Registry registry;
	private final RegistryUpdater registryUpdater;
	private final Scene scene;
	private final GameBus gameBus;

	public SceneLayer(String layerName, Vec3f ambientLight, Fog fog) {

		this.gameBus = new GameBus();

		this.layerName = layerName;
		this.registry = new Registry(gameBus, layerName);
		this.gcsSystems = new ArrayList<>();
		this.registryUpdater = new RegistryUpdater(gcsSystems, registry, gameBus);
		this.scene = new Scene(
				layerName,
				new Shader("/shaders/mainVertex.glsl", "/shaders/mainFragment.glsl"),
				new Shader("/shaders/waterVertex.glsl", "/shaders/waterFragment.glsl"),
				new Shader("/shaders/skyboxVertex.glsl", "/shaders/skyboxFragment.glsl"),
				new Shader("/shaders/pickingVertex.glsl", "/shaders/pickingFragment.glsl"),
				new Shader("/shaders/terrainVertex.glsl", "/shaders/terrainFragment.glsl"),
				fog,
				ambientLight
		);
	}

	public GameBus getGameBus() {
		return gameBus;
	}

	public ArrayList<GcsSystem<Component>> getGcsSystems() {
		return gcsSystems;
	}

	public String getLayerName() {
		return layerName;
	}

	public Registry getRegistry() {
		return registry;
	}

	public RegistryUpdater getRegistryUpdater() {
		return registryUpdater;
	}

	public Scene getScene() {
		return scene;
	}
}
