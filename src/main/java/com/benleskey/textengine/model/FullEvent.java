package com.benleskey.textengine.model;

import com.benleskey.textengine.Game;
import lombok.Getter;

@Getter
public class FullEvent<T extends Reference> extends Event {
	private final T reference;

	public FullEvent(long id, T reference, Game game) {
		super(id, game);
		this.reference = reference;
	}
}
