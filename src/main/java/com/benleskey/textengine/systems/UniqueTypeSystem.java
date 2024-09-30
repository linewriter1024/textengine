package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.model.UniqueType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UniqueTypeSystem extends SingletonGameSystem {
	private final GrouplessPropertiesSubSystem<String, Long> types;
	private final Map<String, Long> cachedTypes = new HashMap<>();

	public UniqueTypeSystem(Game game) {
		super(game);

		types = game.registerSystem(new GrouplessPropertiesSubSystem<>(game, "unique_type", PropertiesSubSystem.stringHandler(), PropertiesSubSystem.longHandler()));
	}

	@Override
	public void initialize() throws DatabaseException {

	}

	public synchronized UniqueType getType(String type) throws DatabaseException {
		return new UniqueType(cachedTypes.computeIfAbsent(type, (_) -> types.get(type).orElseGet(() -> {
			long id = game.getNewGlobalId();
			types.set(type, id);
			this.log.log("Type %s given ID %d", type, id);
			return id;
		})), this);
	}

	public synchronized Optional<String> getTypeLabel(UniqueType type) {
		return this.cachedTypes.entrySet().stream().filter(entry -> entry.getValue() == type.type()).findFirst().map(Map.Entry::getKey);
	}
}
