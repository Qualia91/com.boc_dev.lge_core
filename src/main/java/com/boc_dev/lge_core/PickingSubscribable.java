//package com.boc_dev.lge_core;
//
//import com.boc_dev.event_bus.event_data.PickingResponseEventData;
//import com.boc_dev.event_bus.event_types.ManagementEventType;
//import com.boc_dev.event_bus.event_types.PickingEventType;
//import com.boc_dev.event_bus.events.ManagementEvent;
//import com.boc_dev.event_bus.events.PickingEvent;
//import com.boc_dev.event_bus.interfaces.Event;
//import com.boc_dev.event_bus.interfaces.Subscribable;
//import com.boc_dev.lge_model.gcs.Registry;
//
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.concurrent.ArrayBlockingQueue;
//
//public class PickingSubscribable implements Subscribable, Runnable {
//
//	private final Set<Class<?>> supports = new HashSet<>();
//	private final ArrayBlockingQueue<Event<?>> eventQueue = new ArrayBlockingQueue<>(10);
//	private final ArrayList<Event<?>> drainToList = new ArrayList<>();
//	private final ArrayList<Registry> gameObjects;
//
//	private boolean shutdown = false;
//
//	public PickingSubscribable(ArrayList<GameObject> gameObjects) {
//		supports.add(PickingEvent.class);
//		supports.add(ManagementEvent.class);
//
//		this.gameObjects = gameObjects;
//	}
//
//	@Override
//	public void handle(Event<?> event) {
//		eventQueue.offer(event);
//	}
//
//	@Override
//	public boolean supports(Class<? extends Event> aClass) {
//		return supports.contains(aClass);
//	}
//
//	@Override
//	public void run() {
//		while (!shutdown) {
//
//			eventQueue.drainTo(drainToList);
//
//			for (Event<?> event : drainToList) {
//				actionEvent(event);
//			}
//
//			drainToList.clear();
//
//		}
//	}
//
//	private void actionEvent(Event<?> event) {
//		if (event instanceof ManagementEvent) {
//
//			ManagementEvent managementEvent = (ManagementEvent) event;
//
//			if (managementEvent.getType().equals(ManagementEventType.SHUTDOWN)) {
//				shutdown = true;
//			}
//
//		} else if (event.getType().equals(PickingEventType.RESPONSE)) {
//			PickingEvent pickingEvent = (PickingEvent) event;
//			GeometryGameObject geometryGameObject = (GeometryGameObject) GameObjectUtils.FindGameObjectByID(gameObjects, ((PickingResponseEventData) pickingEvent.getData()).getUuid());
//			System.out.println(geometryGameObject.getBuilder().getName());
//		}
//	}
//}
