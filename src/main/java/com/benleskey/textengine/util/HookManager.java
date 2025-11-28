package com.benleskey.textengine.util;

import com.benleskey.textengine.exceptions.InternalException;

import java.util.*;
import java.util.function.Consumer;

public class HookManager<THandler extends HookHandler> {
	private final Map<Class<? extends HookEvent>, List<THandler>> eventHandlers = new LinkedHashMap<>();

	public void calculateOrder() {
		for (List<THandler> handlers : eventHandlers.values()) {
			handlers.sort(Comparator.comparing(THandler::getEventOrder));
		}
	}

	public <T extends HookEvent> void doEvent(Class<T> pluginEvent, Consumer<T> runner) throws InternalException {
		for (THandler handler : eventHandlers.getOrDefault(pluginEvent, Collections.emptyList())) {
			@SuppressWarnings("unchecked")
			T castHandler = (T) handler;
			runner.accept(castHandler);
		}
	}

	@SuppressWarnings("null") // Generic type THandler will never be null
	public Set<Class<? extends HookEvent>> registerHookHandler(THandler handler) {
		Set<Class<? extends HookEvent>> events = new HashSet<>();

		for (Class<?> event : Interfaces.getAllInterfaces(handler.getClass())) {
			if (HookEvent.class.isAssignableFrom(event)) {
				@SuppressWarnings("unchecked")
				Class<? extends HookEvent> castEvent = (Class<? extends HookEvent>) event;
				events.add(castEvent);
				eventHandlers.compute(castEvent, (k, v) -> {
					List<THandler> handlers = v;
					if (handlers == null) {
						handlers = new ArrayList<>();
					}
					handlers.add(handler);
					return handlers;
				});
			}
		}

		return events;
	}
}
