package com.benleskey.textengine;

public abstract class Plugin {
	private String id;
	protected Game game;

	public Plugin(Game game) {
		this.game = game;
	}

	public String getId() {
		return id == null ? this.getClass().getCanonicalName() : id;
	}

	public abstract void activate();
}
