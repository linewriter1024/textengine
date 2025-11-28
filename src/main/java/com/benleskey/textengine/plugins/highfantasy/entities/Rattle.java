package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.UsableItem;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.systems.EntityDescriptionSystem;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

import java.util.List;
import java.util.Random;

/**
 * A wooden toy rattle that makes a pleasant sound when used.
 */
public class Rattle extends Item implements UsableItem {
	
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
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		String itemName = eds.getSimpleDescription(this, ws.getCurrentTime(), "the rattle");
		
		return CommandOutput.make("use")
			.put("item", this.getKeyId())
			.put("item_name", itemName)
			.text(Markup.concat(
				Markup.raw("You shake "),
				Markup.em(itemName),
				Markup.raw(". It makes a pleasant rattling sound.")
			));
	}
}
