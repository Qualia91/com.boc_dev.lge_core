package com.nick.wood.game_engine.core;

import com.nick.wood.game_engine.event_bus.event_data.PickingResponseEventData;
import com.nick.wood.game_engine.event_bus.event_types.ManagementEventType;
import com.nick.wood.game_engine.event_bus.event_types.PickingEventType;
import com.nick.wood.game_engine.event_bus.events.ManagementEvent;
import com.nick.wood.game_engine.event_bus.events.PickingEvent;
import com.nick.wood.game_engine.event_bus.interfaces.Event;
import com.nick.wood.game_engine.event_bus.interfaces.Subscribable;
import com.nick.wood.game_engine.model.game_objects.GameObject;
import com.nick.wood.game_engine.model.utils.GameObjectUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

public class PickingSubscribable implements Subscribable, Runnable {

	private final Set<Class<?>> supports = new HashSet<>();
	private final ArrayBlockingQueue<Event<?>> eventQueue = new ArrayBlockingQueue<>(100);
	private final ArrayList<Event<?>> drainToList = new ArrayList<>();
	private final ArrayList<GameObject> gameObjects;

	private boolean shutdown = false;

	public PickingSubscribable(ArrayList<GameObject> gameObjects) {
		supports.add(PickingEvent.class);
		supports.add(ManagementEvent.class);

		this.gameObjects = gameObjects;
	}

	@Override
	public void handle(Event<?> event) {
		eventQueue.add(event);
	}

	@Override
	public boolean supports(Class<? extends Event> aClass) {
		return supports.contains(aClass);
	}

	@Override
	public void run() {
		while (!shutdown) {

			eventQueue.drainTo(drainToList);

			for (Event<?> event : drainToList) {
				actionEvent(event);
			}

			drainToList.clear();

		}
	}

	private void actionEvent(Event<?> event) {
		if (event instanceof ManagementEvent) {

			ManagementEvent managementEvent = (ManagementEvent) event;

			if (managementEvent.getType().equals(ManagementEventType.SHUTDOWN)) {
				shutdown = true;
			}

		} else if (event.getType().equals(PickingEventType.RESPONSE)) {
			PickingEvent pickingEvent = (PickingEvent) event;
			GameObject gameObject = GameObjectUtils.FindGameObjectByID(gameObjects, ((PickingResponseEventData) pickingEvent.getData()).getUuid());
			System.out.println(gameObject);
		}
	}
}
