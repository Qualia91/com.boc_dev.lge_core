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
import com.nick.wood.game_engine.event_bus.subscribables.ErrorSubscribable;
import com.nick.wood.game_engine.gcs_model.gcs.RegistryUpdater;
import com.nick.wood.game_engine.gcs_model.bus.RenderableUpdateEvent;
import com.nick.wood.game_engine.gcs_model.bus.RenderableUpdateEventType;
import com.nick.wood.game_engine.gcs_model.gcs.Component;
import com.nick.wood.game_engine.gcs_model.gcs.Registry;
import com.nick.wood.game_engine.gcs_model.generated.components.ComponentType;
import com.nick.wood.game_engine.gcs_model.generated.components.TransformObject;
import com.nick.wood.game_engine.gcs_model.systems.GcsSystem;
import com.nick.wood.game_engine.model.input.ControllerState;
import com.nick.wood.game_engine.systems.control.DirectTransformController;
import com.nick.wood.game_engine.systems.control.GameManagementInputController;
import com.nick.wood.game_engine.systems.control.InputSystemGcs;
import com.nick.wood.graphics_library.RenderEventData;
import com.nick.wood.graphics_library.Window;
import com.nick.wood.graphics_library.WindowInitialisationParameters;
import com.nick.wood.graphics_library.objects.render_scene.Scene;
import com.nick.wood.maths.objects.matrix.Matrix4f;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameLoopGCS implements Subscribable {

	private final Set<Class<?>> supports = new HashSet<>();

	private static final float FPS = 60;
	private final WindowInitialisationParameters wip;
	private final Registry registry;
	private final RenderingConversion renderingConversion;
	private final GameBus gameBus;
	private final ExecutorService executorService;
	private final ArrayList<Scene> sceneLayers;
	private final ArrayList<GcsSystem<Component>> gcsSystems = new ArrayList<>();
	private final InputSystemGcs inputSystem;
	private final RegistryUpdater registryUpdater;
	private final ArrayList<ComponentType> renderComponentTypes = new ArrayList<>();

	// todo check this is actually quicker
	// this is created here once and then added to, passed to render update message creator and then cleared to save time in creation.
	// this should be quicker for arrays with less than 100,000 approx according to the internet, and if the updates get more than that per iteration
	// i should use a proper game engine
	private final ArrayList<UUID> objectIdsToUpdateInRender = new ArrayList<>();

	private final ArrayBlockingQueue<Component> addedRenderableQueue = new ArrayBlockingQueue<>(100);
	private final ArrayBlockingQueue<Component> removedRenderableQueue = new ArrayBlockingQueue<>(100);
	private final ArrayBlockingQueue<TransformObject> updateTransformQueue = new ArrayBlockingQueue<>(100);
	private final ArrayBlockingQueue<Component> updateRenderableQueue = new ArrayBlockingQueue<>(100);
	private final ArrayList<Component> addedRenderable = new ArrayList<>();
	private final ArrayList<Component> removedRenderable = new ArrayList<>();
	private final ArrayList<TransformObject> updateTransform = new ArrayList<>();
	private final ArrayList<Component> updateRenderable = new ArrayList<>();

	private volatile boolean shutdown = false;

	public GameLoopGCS(ArrayList<Scene> sceneLayers,
	                   WindowInitialisationParameters wip,
	                   DirectTransformController directTransformController,
	                   Registry registry) {

		for (ComponentType componentType : ComponentType.values()) {
			if (componentType.isRender()) {
				renderComponentTypes.add(componentType);
			}
		}

		this.supports.add(ManagementEvent.class);
		this.supports.add(RenderableUpdateEvent.class);

		this.executorService = Executors.newCachedThreadPool();
		this.gameBus = new GameBus();
		this.sceneLayers = sceneLayers;
		this.wip = wip;
		this.registry = registry;

		this.renderingConversion = new RenderingConversion();

		ControllerState controllerState = new ControllerState();

		this.executorService.submit(controllerState);

		this.gameBus.register(controllerState);
		this.gameBus.register(this);

		this.gameBus.register(new ErrorSubscribable(System.err::println));

		GameManagementInputController gameManagementInputController = new GameManagementInputController(gameBus);

		this.inputSystem = new InputSystemGcs(controllerState);
		inputSystem.addControl(directTransformController);
		inputSystem.addControl(gameManagementInputController);

		gcsSystems.add((GcsSystem) inputSystem);

		registryUpdater = new RegistryUpdater(gcsSystems);

	}

	public void render() {
		Window window = new Window(sceneLayers, gameBus);

		long steps = 0;

		long lastTime = System.nanoTime();

		double deltaSeconds;

		try {
			window.init(wip);
		} catch (IOException e) {
			gameBus.dispatch(new ErrorEvent(e, ErrorEventType.CRITICAL));
			return;
		}

		this.gameBus.register(window);

		while (!window.shouldClose()) {
			steps++;
			window.render(steps);
			deltaSeconds = (System.nanoTime() - lastTime) / 1000000000.0;
			double fps = 1 / deltaSeconds;
			if (fps < 50) {
				window.setTitle("FPS dropped below 50 to: " + fps);
			}
			lastTime = System.nanoTime();
		}

		window.close();
		shutdown = true;
		executorService.shutdownNow();
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

					registryUpdater.run(step);

					// build graphics engine model update message
					// get all change lists that renderer is interested in
					addedRenderableQueue.drainTo(addedRenderable);
					removedRenderableQueue.drainTo(removedRenderable);
					updateTransformQueue.drainTo(updateTransform);
					updateRenderableQueue.drainTo(updateRenderable);

					// first iterate over all transforms and check if they are dirty
					// if they are, walk up the tree to find the highest transform that is dirty,
					// then walk back down the tree, updating the transforms as you go, and sending
					// updates to graphics engine about renderable component updates

					for (TransformObject transformObject : updateTransform) {
						// first check if current transform has flag set to true anymore as another walk may have resolved it
						if (transformObject.isDirty()) {
							TransformObject rootDirtyTransform = findRootDirtyTransform(transformObject);
						}
					}

					// send component creation and removal requests to graphics engine
//					for (Component component : addedRenderable) {
//						gameBus.dispatch(new RenderEvent(
//								new RenderEventData(step, stringArrayListEntry.getKey(),
//										renderLists),
//								RenderEventType.UPDATE
//						));
//					}



					addedRenderable.clear();
					removedRenderable.clear();
					updateTransform.clear();
					updateRenderable.clear();

					deltaSeconds = 0;
				}


				lastTime = now;

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	// TODO definitely write tests for this, who knows if this works...
	// this function steps up the tree untill it gets to the root node. from there it steps downwards to find the
	// highest transform that has a dirty flag set to true. If it finds one it will return it. If it doesnt (ie all
	// transforms has been resolved) it returns null.
	TransformObject findRootDirtyTransform(Component component) {
		// if component has parent, get parent and run again
		if (component.getParent() != null) {
			TransformObject returnedComponent = findRootDirtyTransform(component.getParent());
			// if the returned component is not null, then we have found the highest transform with a dirty flag set true
			// so return it
			if (returnedComponent != null) {
				return returnedComponent;
			}
			// if it is null, we need to check this layers component for whether its a transform and dirty
			if (component.getComponentType().equals(ComponentType.TRANSFORM) && component.isDirty()) {
				// if it is a transform and dirty, return this to be entered into the transform resolver function
				return (TransformObject) component;
			}
			// if it is not, return null again so next layer can check
			return null;
		}
		// if it doesn't have parent, it is root node and we need to check for type and dirty flag
		else if (component.getComponentType().equals(ComponentType.TRANSFORM) && component.isDirty()) {
			// if it is a transform and dirty, return this to be entered into the transform resolver function
			return (TransformObject) component;
		}
		// if it doesn't and its not a transform and dirt, return null so the next level down can be checked
		else {
			return null;
		}
	}

	// this function takes in a component and a current world transform (as a matrix4f) and walks down the tree,
	// calculating the current transform if it comes across a transform object, or sending an update to the
	// rendering module if it comes across a renderable, with its id and a new matrix4f for it
	private Matrix4f resolveTransformsAndSend(Component component, Matrix4f currentGlobalTransform) {

		// if its a transform object, update the currentGlobalTransform
		if (component.getComponentType().equals(ComponentType.TRANSFORM)) {
			TransformObject transformObject = (TransformObject) component;
			currentGlobalTransform = currentGlobalTransform.multiply(
					Matrix4f.Transform(
							transformObject.getPosition(),
							transformObject.getRotation().toMatrix(),
							transformObject.getScale()));
		}
		// if the component is a renderable, send an update to the graphics updating the instance of it
//		else if (component.getComponentType().isRender()) {
//			gameBus.dispatch(new RenderInstanceUpdateEvent(uuid,
//					RenderEventType.UPDATE
//			));
//		}
		return null;
	}

	public InputSystemGcs getInputSystem() {
		return inputSystem;
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

	public ArrayList<GcsSystem<Component>> getGcsSystems() {
		return gcsSystems;
	}
}
