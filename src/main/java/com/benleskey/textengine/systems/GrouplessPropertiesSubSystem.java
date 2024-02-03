package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.GameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;

import java.util.Optional;

public class GrouplessPropertiesSubSystem<TProperty, TValue> extends GameSystem {
	private static final long GROUP = 0;
	private PropertiesSubSystem<Long, TProperty, TValue> properties;

	public GrouplessPropertiesSubSystem(Game game, String tableName, PropertiesSubSystem.Handler<TProperty> property, PropertiesSubSystem.Handler<TValue> value) {
		super(game);
		properties = new PropertiesSubSystem<>(game, tableName, PropertiesSubSystem.longHandler(), property, value);
	}

	@Override
	public void initialize() throws DatabaseException {
		properties.initialize();
	}

	@Override
	public String getId() {
		return super.getId() + "$" + properties.getTableName();
	}

	Optional<TValue> get(TProperty property) throws DatabaseException {
		return properties.get(GROUP, property);
	}

	void set(TProperty property, TValue value) throws DatabaseException {
		properties.set(GROUP, property, value);
	}
}
