package com.benleskey.textengine.model;

import com.benleskey.textengine.Game;

public class Reference {
	private final long id;
	private final Game game;

	public Reference(long id, Game game) {
		this.id = id;
		this.game = game;
	}

	public long getId() {
		return id;
	}

	public Game getGame() {
		return game;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "#" + id;
	}
}
