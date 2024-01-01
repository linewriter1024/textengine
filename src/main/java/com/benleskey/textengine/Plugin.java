package com.benleskey.textengine;

public abstract class Plugin {
	protected Game game;

	public Plugin(Game game) {
		this.game = game;
	}

	public String getId() {
		return this.getClass().getCanonicalName();
	}

	public abstract void activate();
}
