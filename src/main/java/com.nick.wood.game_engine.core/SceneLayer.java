package com.nick.wood.game_engine.core;

import com.nick.wood.game_engine.event_bus.busses.GameBus;
import com.nick.wood.game_engine.gcs_model.gcs.Component;
import com.nick.wood.game_engine.gcs_model.gcs.Registry;
import com.nick.wood.game_engine.gcs_model.gcs.RegistryUpdater;
import com.nick.wood.game_engine.gcs_model.systems.GcsSystem;
import com.nick.wood.graphics_library.Shader;
import com.nick.wood.graphics_library.objects.lighting.Fog;
import com.nick.wood.graphics_library.objects.render_scene.Scene;
import com.nick.wood.maths.objects.vector.Vec3f;

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
		this.registry = new Registry(gameBus);
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
