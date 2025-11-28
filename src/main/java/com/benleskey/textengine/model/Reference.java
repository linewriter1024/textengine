package com.benleskey.textengine.model;

import com.benleskey.textengine.Game;
import lombok.Getter;

@Getter
public class Reference {
	protected final long id;
	protected final Game game;

	public Reference(long id, Game game) {
		this.id = id;
		this.game = game;
	}

	public String getKeyId() {
		return Long.toString(id);
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
		if (this == o)
			return true;
		if (!(o instanceof Reference reference))
			return false;
		return id == reference.id;
	}
}
