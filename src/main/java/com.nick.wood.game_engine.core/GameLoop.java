package com.nick.wood.game_engine.core;

import com.nick.wood.game_engine.event_bus.busses.GameBus;
import com.nick.wood.game_engine.event_bus.event_types.ErrorEventType;
import com.nick.wood.game_engine.event_bus.event_types.ManagementEventType;
import com.nick.wood.game_engine.event_bus.event_types.RenderEventType;
import com.nick.wood.game_engine.event_bus.events.ErrorEvent;
import com.nick.wood.game_engine.event_bus.events.ManagementEvent;
import com.nick.wood.game_engine.event_bus.events.RenderEvent;
import com.nick.wood.game_engine.event_bus.interfaces.Bus;
import com.nick.wood.game_engine.event_bus.interfaces.Event;
import com.nick.wood.game_engine.event_bus.interfaces.Subscribable;
import com.nick.wood.game_engine.model.game_objects.GameObject;
import com.nick.wood.game_engine.model.input.ControllerState;
import com.nick.wood.game_engine.systems.*;
import com.nick.wood.game_engine.systems.control.DirectTransformController;
import com.nick.wood.game_engine.systems.control.GameManagementInputController;
import com.nick.wood.game_engine.systems.control.InputSystem;
import com.nick.wood.graphics_library.RenderEventData;
import com.nick.wood.graphics_library.Window;
import com.nick.wood.graphics_library.WindowInitialisationParameters;
import com.nick.wood.graphics_library.objects.render_scene.RenderGraph;
import com.nick.wood.graphics_library.objects.render_scene.Scene;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameLoop implements Subscribable {

	private final Set<Class<?>> supports = new HashSet<>();

	private static final float FPS = 60;
	private final WindowInitialisationParameters wip;
	private final HashMap<String, ArrayList<GameObject>> layeredGameObjectsMap;
	private final RenderingConversion renderingConversion;
	private final GameBus gameBus;
	private final ExecutorService executorService;
	private final ArrayList<Scene> sceneLayers;
	private final ArrayList<GESystem> geSystems = new ArrayList<>();

	private volatile boolean shutdown = false;

	public GameLoop(ArrayList<Scene> sceneLayers,
	                WindowInitialisationParameters wip,
	                DirectTransformController directTransformController,
	                HashMap<String, ArrayList<GameObject>> layeredGameObjectsMap) {

		this.supports.add(ManagementEvent.class);

		this.executorService = Executors.newCachedThreadPool();
		this.gameBus = new GameBus();
		this.sceneLayers = sceneLayers;
		this.wip = wip;
		this.layeredGameObjectsMap = layeredGameObjectsMap;

		this.renderingConversion = new RenderingConversion();

		ControllerState controllerState = new ControllerState();

		this.executorService.submit(controllerState);

		this.gameBus.register(controllerState);
		this.gameBus.register(this);

		GameManagementInputController gameManagementInputController = new GameManagementInputController(gameBus);

		InputSystem inputSystem = new InputSystem(controllerState);
		inputSystem.addControl(directTransformController);
		inputSystem.addControl(gameManagementInputController);

		geSystems.add(inputSystem);

	}

	public void render() {
		Window window = new Window(sceneLayers, gameBus);

		long steps = 0;

		long lastTime = System.nanoTime();

		double deltaSeconds = 0;

		try {
			window.init(wip);
		} catch (IOException e) {
			gameBus.dispatch(new ErrorEvent(e, ErrorEventType.CRITICAL));
			return;
		}

		this.gameBus.register(window);

		while (!window.shouldClose()) {
			steps++;
			long now = System.nanoTime();
			window.render(steps);
			deltaSeconds += (now - lastTime) / 1000000000.0;
			window.setTitle("FPS: " + 1 / deltaSeconds);
			deltaSeconds = 0.0;
			lastTime = now;
		}

		window.close();
		shutdown = true;
		executorService.shutdownNow();
	}

	public void update() {

		long step = 0;
		long lastTime = System.nanoTime();
		long startTime = System.nanoTime();

		double deltaSeconds = 0;

		while (!shutdown) {

			try {

				long now = System.nanoTime();

				deltaSeconds += (now - lastTime) / 1000000000.0;

				if (deltaSeconds >= 1 / FPS) {

					step++;

					// update systems
					for (GESystem geSystem : geSystems) {
						geSystem.update(layeredGameObjectsMap, now - startTime);
					}

					// convert to render-able objects and send to be rendered
					for (Map.Entry<String, ArrayList<GameObject>> stringArrayListEntry : layeredGameObjectsMap.entrySet()) {

						updateObjectTree(stringArrayListEntry.getValue());

						RenderGraph renderLists = renderingConversion.createRenderLists(stringArrayListEntry.getValue(), step);

						gameBus.dispatch(new RenderEvent(
								new RenderEventData(step, stringArrayListEntry.getKey(),
										renderLists),
								RenderEventType.UPDATE
						));
					}

					deltaSeconds = 0;
				}


				lastTime = now;

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	// returns true if update occurred
	private void updateObjectTree(List<GameObject> objectTree) {
		for (GameObject gameObject : objectTree) {
			gameObject.getGameObjectData().getUpdater().applyUpdates();
			updateObjectTree(gameObject.getGameObjectData().getChildren());
		}
	}

	public Bus getGameBus() {
		return gameBus;
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}

	@Override
	public void handle(Event<?> event) {

		if (event.getType().equals(ManagementEventType.SHUTDOWN)) {
			shutdown = true;
			executorService.shutdown();
		}
	}

	@Override
	public boolean supports(Class<? extends Event> aClass) {
		return supports.contains(aClass);
	}

	public ArrayList<GESystem> getGESystems() {
		return geSystems;
	}
}
