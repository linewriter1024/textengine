package com.benleskey.textengine;

import com.benleskey.textengine.util.HookHandler;
import com.benleskey.textengine.util.Logger;

import java.util.Set;

public abstract class Plugin implements HookHandler {
	protected Game game;
	protected Logger log;

	public Plugin(Game game) {
		this.game = game;
		this.log = game.log.withPrefix(this.getClass().getSimpleName());
	}

	public String getId() {
		return this.getClass().getCanonicalName();
	}

	public Set<Plugin> getDependencies() {
		return Set.of();
	}

	@Override
	public int getEventOrder() {
		return 1 + this.getDependencies().stream().mapToInt(Plugin::getEventOrder).sum();
	}
}
