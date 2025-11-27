package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.UniqueType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UniqueTypeSystem extends SingletonGameSystem implements OnSystemInitialize {
	private final GrouplessPropertiesSubSystem<String, Long> types;
	private final Map<String, Long> cachedTypes = new HashMap<>();

	public UniqueTypeSystem(Game game) {
		super(game);

		types = game.registerSystem(new GrouplessPropertiesSubSystem<>(game, "unique_type", PropertiesSubSystem.stringHandler(), PropertiesSubSystem.longHandler()));
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		// No types defined here - they're defined in the systems that use them
	}

	public synchronized UniqueType getType(String type) throws DatabaseException {
		return getTypeFromRaw(cachedTypes.computeIfAbsent(type, (key) -> types.get(type).orElseGet(() -> {
			long id = game.getNewGlobalId();
			types.set(type, id);
			this.log.log("Type %s given ID %d", type, id);
			return id;
		})));
	}

	public synchronized Optional<String> getTypeLabel(UniqueType type) {
		return this.cachedTypes.entrySet().stream().filter(entry -> entry.getValue() == type.type()).findFirst().map(Map.Entry::getKey);
	}

	public UniqueType getTypeFromRaw(long raw) {
		return new UniqueType(raw, this);
	}
}
