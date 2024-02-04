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

	public String getKeyId() {
		return Long.toString(id);
	}

	public Game getGame() {
		return game;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "#" + id;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(getId());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || !(o instanceof Reference)) return false;
		Reference reference = (Reference) o;
		return id == reference.id;
	}
}
