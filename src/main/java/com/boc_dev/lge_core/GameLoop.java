package com.boc_dev.lge_core;

import com.boc_dev.event_bus.busses.GameBus;
import com.boc_dev.event_bus.event_data.PickingResponseEventData;
import com.boc_dev.event_bus.event_types.ErrorEventType;
import com.boc_dev.event_bus.event_types.ManagementEventType;
import com.boc_dev.event_bus.event_types.PickingEventType;
import com.boc_dev.event_bus.events.ErrorEvent;
import com.boc_dev.event_bus.events.ManagementEvent;
import com.boc_dev.event_bus.events.PickingEvent;
import com.boc_dev.event_bus.interfaces.Event;
import com.boc_dev.event_bus.interfaces.Subscribable;
import com.boc_dev.event_bus.subscribables.ErrorSubscribable;
import com.boc_dev.lge_model.bus.RenderableUpdateEvent;
import com.boc_dev.lge_model.bus.RenderableUpdateEventType;
import com.boc_dev.lge_model.gcs.Component;
import com.boc_dev.lge_model.generated.components.ComponentType;
import com.boc_dev.lge_model.generated.components.TransformObject;
import com.boc_dev.lge_model.systems.GcsSystem;
import com.boc_dev.lge_systems.control.ControllerState;
import com.boc_dev.lge_systems.control.InputSystem;
import com.boc_dev.graphics_library.Window;
import com.boc_dev.graphics_library.WindowInitialisationParameters;
import com.boc_dev.graphics_library.objects.render_scene.Scene;
import com.boc_dev.lge_systems.control.PickingSystem;
import com.boc_dev.maths.objects.matrix.Matrix4f;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameLoop implements Subscribable {

	private final Set<Class<?>> supports = new HashSet<>();

	private static final float FPS = 60;
	private final WindowInitialisationParameters wip;
	private final RenderingConversion renderingConversion;
	private final GameBus renderGameBus;
	private final ExecutorService executorService;
	private final ArrayList<SceneLayer> sceneLayers;
	private final ArrayList<ComponentType> renderComponentTypes = new ArrayList<>();

	private final ArrayBlockingQueue<Component> addedRenderableQueue = new ArrayBlockingQueue<>(1_000_000);
	private final ArrayBlockingQueue<Component> removedRenderableQueue = new ArrayBlockingQueue<>(1_000_000);
	private final ArrayBlockingQueue<TransformObject> updateTransformQueue = new ArrayBlockingQueue<>(1_000_000);
	private final ArrayBlockingQueue<Component> updateRenderableQueue = new ArrayBlockingQueue<>(1_000_000);
	private final ArrayList<Component> addedRenderable = new ArrayList<>();
	private final ArrayList<Component> removedRenderable = new ArrayList<>();
	private final ArrayList<TransformObject> updateTransform = new ArrayList<>();
	private final ArrayList<Component> updateRenderable = new ArrayList<>();
	private final Window window;

	private volatile boolean shutdown = false;

	private final TreeUtils treeUtils = new TreeUtils();

	public GameLoop(ArrayList<SceneLayer> sceneLayers,
	                WindowInitialisationParameters wip) {

		for (ComponentType componentType : ComponentType.values()) {
			if (componentType.isRender()) {
				renderComponentTypes.add(componentType);
			}
		}

		this.supports.add(ManagementEvent.class);
		this.supports.add(RenderableUpdateEvent.class);

		this.renderGameBus = new GameBus();
		this.renderGameBus.register(this);

		this.executorService = Executors.newCachedThreadPool();

		this.renderingConversion = new RenderingConversion(renderGameBus);

		this.sceneLayers = sceneLayers;
		this.wip = wip;

		ControllerState controllerState = new ControllerState();
		this.executorService.submit(controllerState);
		this.renderGameBus.register(controllerState);

        ErrorSubscribable errorSubscribable = new ErrorSubscribable(System.err::println);
        this.renderGameBus.register(errorSubscribable);

		ArrayList<Scene> scenes = new ArrayList<>();
		for (SceneLayer sceneLayer : sceneLayers) {
			scenes.add(sceneLayer.getScene());
		}

		this.window = new Window(scenes, renderGameBus);

		this.renderGameBus.register(window);

		for (SceneLayer sceneLayer : sceneLayers) {
			sceneLayer.getGameBus().register(this);
			sceneLayer.getGameBus().register(controllerState);
			sceneLayer.getGameBus().register(errorSubscribable);
			InputSystem inputSystem = new InputSystem(controllerState, sceneLayer.getGameBus());
			sceneLayer.getGcsSystems().add((GcsSystem) inputSystem);
			PickingSystem pickingSystem = new PickingSystem();
			sceneLayer.getGcsSystems().add((GcsSystem) pickingSystem);
			this.renderGameBus.register(pickingSystem);
			sceneLayer.getGameBus().register(window);
		}

		executorService.submit(errorSubscribable);
	}

	public void render() {

		long steps = 0;

		long lastTime = System.nanoTime();

		double deltaSeconds;

		try {
			window.init(wip);
		} catch (IOException e) {
			renderGameBus.dispatch(new ErrorEvent(e, ErrorEventType.CRITICAL));
			window.close();
			shutdown = true;
		}

		while (!window.shouldClose()) {
			steps++;
			window.render(steps);
			deltaSeconds = (System.nanoTime() - lastTime) / 1000000000.0;
			window.setTitle("FPS: " + Math.round(1.0 / deltaSeconds));
			lastTime = System.nanoTime();
		}

		window.close();
		shutdown = true;
	}

	public void update() {

		long step = 0;
		long lastTime = System.nanoTime();

		double deltaSeconds = 0;

		while (!shutdown) {

			try {

				long now = System.nanoTime();

				deltaSeconds += (now - lastTime) / 1000000000.0;

				if (deltaSeconds >= 1 / FPS) {

					step++;

					for (SceneLayer sceneLayer : sceneLayers) {

						sceneLayer.getRegistryUpdater().run(step);

						// build graphics engine model update message
						// get all change lists that renderer is interested in
						addedRenderableQueue.drainTo(addedRenderable);
						removedRenderableQueue.drainTo(removedRenderable);
						updateTransformQueue.drainTo(updateTransform);
						updateRenderableQueue.drainTo(updateRenderable);

						renderingConversion.setLayerName(sceneLayer.getLayerName());

						// first iterate over all transforms and check if they are dirty
						// if they are, walk up the tree to find the highest transform that is dirty,
						// then walk back down the tree, updating the transforms as you go, and sending
						// updates to graphics engine about renderable component updates
						for (TransformObject transformObject : updateTransform) {
							// first check if current transform has flag set to true anymore as another walk may have resolved it
							if (transformObject.isDirty()) {
								TransformObject rootDirtyTransform = treeUtils.findRootDirtyTransform(transformObject);

								// then resolve all transforms
								treeUtils.resolveTransformsAndSend(rootDirtyTransform, Matrix4f.Identity, renderingConversion);
							}
						}

						for (Component component : addedRenderable) {
							renderingConversion.sendComponentCreateUpdate(component);
						}

						for (Component component : removedRenderable) {
							renderingConversion.sendComponentDeleteUpdate(component);
						}

						// now iterate over the updated renderables and send type update changes to graphics
						// engine
						for (Component component : updateRenderable) {
							renderingConversion.updateRenderableComponentType(component);
						}

						renderingConversion.send();

						addedRenderable.clear();
						removedRenderable.clear();
						updateTransform.clear();
						updateRenderable.clear();

					}

					deltaSeconds = 0;
				}


				lastTime = now;

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	@Override
	public void handle(Event<?> event) {

		if (event.getType().equals(ManagementEventType.SHUTDOWN)) {
			System.out.println("Shutting down");
			shutdown = true;
			executorService.shutdownNow();
		} else if (event.getType().equals(RenderableUpdateEventType.CREATE)) {
			addedRenderableQueue.offer((Component) event.getData());
		} else if (event.getType().equals(RenderableUpdateEventType.DESTROY)) {
			removedRenderableQueue.offer((Component) event.getData());
		} else if (event.getType().equals(RenderableUpdateEventType.UPDATE_TRANSFORM)) {
			updateTransformQueue.offer((TransformObject) event.getData());
		} else if (event.getType().equals(RenderableUpdateEventType.UPDATE_RENDERABLE)) {
			updateRenderableQueue.offer((Component) event.getData());
		}
	}

	@Override
	public boolean supports(Class<? extends Event> aClass) {
		return supports.contains(aClass);
	}

	public void start() {

		executorService.execute(() -> {
			while(!Thread.currentThread().isInterrupted()) {
				this.render();
			}
		});

		executorService.execute(() -> {
			while(!Thread.currentThread().isInterrupted()) {
				this.update();
			}
		});
	}
}
