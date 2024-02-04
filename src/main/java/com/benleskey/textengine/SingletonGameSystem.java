package com.benleskey.textengine;

public abstract class SingletonGameSystem extends GameSystem {

	public SingletonGameSystem(Game game) {
		super(game);
	}

	public static <T extends SingletonGameSystem> String getSingletonGameSystemId(Class<T> c) {
		return c.getCanonicalName();
	}

	@Override
	public final String getId() {
		return getSingletonGameSystemId(this.getClass());
	}
}
