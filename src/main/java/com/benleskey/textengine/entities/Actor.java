package com.benleskey.textengine.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.model.BaseEntity;
import com.benleskey.textengine.model.DTime;

public class Actor extends BaseEntity implements Acting {

	public Actor(long id, Game game) {
		super(id, game);
	}

	@Override
	public void onActionReady() {
		// Default: do nothing. Subclasses (like Avatar) or NPC AI systems
		// will override or handle this through plugins.
	}

	@Override
	public DTime getActionInterval() {
		// Default interval for checking action readiness
		return DTime.fromSeconds(60);
	}
}
