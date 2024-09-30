package com.benleskey.textengine;

import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.util.Logger;

public abstract class Plugin {
	protected Game game;
	protected Logger log;

	public Plugin(Game game) {
		this.game = game;
		this.log = game.log.withPrefix(this.getClass().getSimpleName());
	}

	public String getId() {
		return this.getClass().getCanonicalName();
	}

	public void initialize() {
	}

	public void start() throws InternalException {
	}

	public void startClient(Client client) throws InternalException {
	}

}
