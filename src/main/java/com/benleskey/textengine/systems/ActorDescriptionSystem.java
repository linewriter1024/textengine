package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.LookDescriptor;

import java.util.List;

/**
 * System for generating consistent actor descriptions in broadcasts.
 * Handles the difference between player actors (show as "Player <id>") 
 * and NPC actors (show with look description).
 */
public class ActorDescriptionSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	private EntitySystem entitySystem;
	private EntityTagSystem entityTagSystem;
	private LookSystem lookSystem;
	
	public ActorDescriptionSystem(Game game) {
		super(game);
	}
	
	@Override
	public void onSystemInitialize() {
		entitySystem = game.getSystem(EntitySystem.class);
		entityTagSystem = game.getSystem(EntityTagSystem.class);
		lookSystem = game.getSystem(LookSystem.class);
	}
	
	/**
	 * Get a description of an actor suitable for broadcast messages.
	 * Players: "Player <id>"
	 * NPCs: look description with article (e.g., "a goblin")
	 */
	public String getActorDescription(Actor actor, DTime currentTime) {
		// Check if this is a player (has TAG_AVATAR)
		boolean isPlayer = entityTagSystem.hasTag(actor, entitySystem.TAG_AVATAR, currentTime);
		
		if (isPlayer) {
			return "Player " + actor.getId();
		} else {
			// NPC - use look description
			List<LookDescriptor> looks = lookSystem.getLooksFromEntity(actor, currentTime);
			if (!looks.isEmpty()) {
				String desc = looks.get(0).getDescription();
				// Add article if not already present
				if (!desc.startsWith("a ") && !desc.startsWith("an ") && !desc.startsWith("the ")) {
					return "a " + desc;
				}
				return desc;
			}
			return "someone";
		}
	}
}
