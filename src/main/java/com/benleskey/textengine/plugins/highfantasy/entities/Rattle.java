package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.UsableItem;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.systems.BroadcastSystem;
import com.benleskey.textengine.systems.EntityDescriptionSystem;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

import java.util.Random;

/**
 * A wooden toy rattle that makes a pleasant sound when used.
 */
public class Rattle extends Item implements UsableItem {
	
	// Command constants
	public static final String CMD_USE_RATTLE = "use_rattle";
	public static final String BROADCAST_USE_RATTLE = "use_rattle";
	public static final String M_ACTOR_ID = "actor_id";
	public static final String M_ITEM_ID = "item_id";
	public static final String M_ITEM_NAME = "item_name";
	
	private static final String[] DESCRIPTIONS = {
		"a wooden toy rattle",
		"a painted rattle",
		"a carved rattle"
	};
	
	public Rattle(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a rattle with a randomly selected description variant.
	 * 
	 * @param game The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured rattle entity
	 */
	public static Rattle create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];
		
		Rattle rattle = es.add(Rattle.class);
		ls.addLook(rattle, "basic", description);
		is.addTag(rattle, is.TAG_TOY);
		is.addTag(rattle, is.TAG_TAKEABLE);
		is.addTag(rattle, is.TAG_WEIGHT, 100); // 100g
		
		return rattle;
	}
	
	@Override
	public CommandOutput useSolo(Client client, Entity actor) {
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		String actorDesc = eds.getActorDescription((com.benleskey.textengine.entities.Actor) actor, ws.getCurrentTime());
		String itemName = eds.getSimpleDescription(this, ws.getCurrentTime(), "the rattle");
		
		// Broadcast to all entities in the same location
		CommandOutput broadcast = CommandOutput.make(BROADCAST_USE_RATTLE)
			.put(M_ACTOR_ID, actor.getKeyId())
			.put(M_ITEM_ID, this.getKeyId())
			.put(M_ITEM_NAME, itemName)
			.text(Markup.concat(
				Markup.escape(capitalize(actorDesc)),
				Markup.raw(" shakes "),
				Markup.em(itemName),
				Markup.raw(". It makes a pleasant rattling sound.")
			));
		
		bs.broadcast(actor, broadcast);
		return broadcast;
	}
	
	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
