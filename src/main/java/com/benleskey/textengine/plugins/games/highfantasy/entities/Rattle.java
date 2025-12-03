package com.benleskey.textengine.plugins.games.highfantasy.entities;

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
	// Note: EntitySystem.M_ACTOR_ID, EntitySystem.M_ACTOR_NAME defined in
	// EntitySystem

	// Note: ItemSystem.M_ITEM_ID, ItemSystem.M_ITEM_NAME, ItemSystem.M_WEIGHT,
	// ItemSystem.M_CARRY_WEIGHT defined in ItemSystem

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
	 * @param game   The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured rattle entity
	 */
	public static Rattle create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);

		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];

		Rattle rattle = es.add(Rattle.class);
		ls.addLook(rattle, ls.LOOK_BASIC, description);
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

		String actorDesc = eds.getDescriptionWithArticle(actor,
				ws.getCurrentTime(), "someone");
		String itemName = eds.getSimpleDescription(this, ws.getCurrentTime(), "the rattle");

		// Broadcast to all entities in the same location (using new markup system)
		CommandOutput broadcast = CommandOutput.make(BROADCAST_USE_RATTLE)
				.put(EntitySystem.M_ACTOR_ID, actor.getKeyId())
				.put(ItemSystem.M_ITEM_ID, this.getKeyId())
				.put(ItemSystem.M_ITEM_NAME, itemName)
				.text(Markup.concat(
						Markup.capital(Markup.entity(actor.getKeyId(), actorDesc)),
						Markup.raw(" "),
						Markup.verb("shake", "shakes"),
						Markup.raw(" "),
						Markup.em(itemName),
						Markup.raw(". It makes a pleasant rattling sound.")));

		bs.broadcast(actor, broadcast);
		return broadcast;
	}
}
