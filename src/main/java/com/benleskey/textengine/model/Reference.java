package com.benleskey.textengine;

public class Reference {
	private final String id;
	private final Game game;

	public Reference(String id, Game game) {
		this.id = id;
		this.game = game;
	}

	public String getId() {
		return id;
	}

	public Game getGame() {
		return game;
	}
}
