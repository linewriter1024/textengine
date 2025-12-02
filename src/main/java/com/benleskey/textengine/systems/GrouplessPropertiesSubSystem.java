package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;

import java.util.Optional;

public class GrouplessPropertiesSubSystem<TProperty, TValue> extends PropertiesSubSystem<Long, TProperty, TValue> {
	private static final long GROUP = 0;

	public GrouplessPropertiesSubSystem(Game game, String tableName, PropertiesSubSystem.Handler<TProperty> property,
			PropertiesSubSystem.Handler<TValue> value) {
		super(game, tableName, PropertiesSubSystem.longHandler(), property, value);
	}

	public Optional<TValue> get(TProperty property) {
		return get(GROUP, property);
	}

	public void set(TProperty property, TValue value) {
		set(GROUP, property, value);
	}

	public boolean insertIfAbsent(TProperty property, TValue value)
			throws com.benleskey.textengine.exceptions.DatabaseException {
		return insertIfAbsent(GROUP, property, value);
	}
}
