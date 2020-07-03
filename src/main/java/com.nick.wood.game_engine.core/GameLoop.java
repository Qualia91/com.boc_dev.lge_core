package com.nick.wood.game_engine.core;

import com.nick.wood.event_bus.busses.GameBus;
import com.nick.wood.event_bus.interfaces.Bus;
import com.nick.wood.game_engine.model.game_objects.GameObject;
import com.nick.wood.game_engine.model.input.ControllerState;
import com.nick.wood.game_engine.model.input.DirectTransformController;
import com.nick.wood.game_engine.model.input.GameManagementInputController;
import com.nick.wood.graphics_library.Picking;
import com.nick.wood.graphics_library.Window;
import com.nick.wood.graphics_library.WindowInitialisationParameters;
import com.nick.wood.graphics_library.objects.render_scene.RenderGraph;
import com.nick.wood.graphics_library.objects.render_scene.Scene;
import com.nick.wood.maths.objects.matrix.Matrix4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameLoop {

	private static final float FPS = 60;
	private final WindowInitialisationParameters wip;
	private final DirectTransformController directTransformController;
	private final HashMap<String, ArrayList<GameObject>> layeredGameObjectsMap;
	private final HashMap<String, RenderGraph> renderGraphLayerMap;
	private final RenderingConversion renderingConversion;
	private final GameBus gameBus;
	private final ControllerState controllerState;
	private final ExecutorService executorService;
	private final GameManagementInputController gameManagementInputController;
	private final Window window;

	public GameLoop(ArrayList<Scene> sceneLayers,
	                WindowInitialisationParameters wip,
	                DirectTransformController directTransformController,
	                HashMap<String, ArrayList<GameObject>> layeredGameObjectsMap) {

		this.wip = wip;
		this.layeredGameObjectsMap = layeredGameObjectsMap;
		this.gameBus = new GameBus();

		this.executorService = Executors.newFixedThreadPool(4);

		this.directTransformController = directTransformController;

		this.renderGraphLayerMap = new HashMap<>();
		for (String layerName : layeredGameObjectsMap.keySet()) {
			renderGraphLayerMap.put(layerName, new RenderGraph());
		}

		this.renderingConversion = new RenderingConversion();

		this.controllerState = new ControllerState();

		this.gameManagementInputController = new GameManagementInputController(gameBus);

		this.executorService.submit(controllerState);

		this.gameBus.register(controllerState);

		this.window = new Window(sceneLayers, gameBus);
		this.gameBus.register(window);

		for (Scene sceneLayer : sceneLayers) {
			if (sceneLayer.getPickingShader() != null) {
				this.gameBus.register(new Picking(gameBus, sceneLayer, renderGraphLayerMap.get(sceneLayer.getName())));
			}
		}

	}

	public void run(Runnable ... runnables) throws IOException {

		window.init(wip);

		long lastTime = System.nanoTime();

		double deltaSeconds = 0.0;

		while (!window.shouldClose()) {

			directTransformController.updateUserInput(controllerState);
			gameManagementInputController.updateUserInput(controllerState);

			long now = System.nanoTime();

			deltaSeconds += (now - lastTime) / 1000000000.0;

			if (deltaSeconds >= 1/FPS) {

				for (Runnable runnable : runnables) {
					runnable.run();
				}

				for (Map.Entry<String, ArrayList<GameObject>> stringArrayListEntry : layeredGameObjectsMap.entrySet()) {

					RenderGraph renderGraph = renderGraphLayerMap.get(stringArrayListEntry.getKey());

					renderGraph.empty();

					for (GameObject gameObject : stringArrayListEntry.getValue()) {
						renderingConversion.createRenderLists(renderGraph,
								gameObject,
								Matrix4f.Identity);
					}
				}

				window.setTitle("FPS: " + 1/deltaSeconds);

				deltaSeconds = 0.0;

			}

			window.loop(renderGraphLayerMap);

			lastTime = now;

		}

		window.close();

		executorService.shutdown();
	}

	public Bus getGameBus() {
		return gameBus;
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}
}
