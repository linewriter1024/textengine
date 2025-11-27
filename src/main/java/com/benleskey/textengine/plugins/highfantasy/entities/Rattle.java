package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.UsableItem;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

import java.util.List;

/**
 * A wooden toy rattle that makes a pleasant sound when used.
 */
public class Rattle extends Item implements UsableItem {
	
	public Rattle(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a rattle with the specified description.
	 * Adds basic look and TAG_TOY automatically.
	 * 
	 * @param game The game instance
	 * @param description The description of the rattle (e.g., "a wooden toy rattle")
	 * @return The created and configured rattle entity
	 */
	public static Rattle create(Game game, String description) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		Rattle rattle = es.add(Rattle.class);
		ls.addLook(rattle, "basic", description);
		is.addTag(rattle, is.TAG_TOY);
		
		return rattle;
	}
	
	@Override
	public CommandOutput useSolo(Client client, Entity actor) {
		LookSystem ls = game.getSystem(LookSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		List<LookDescriptor> looks = ls.getLooksFromEntity(this, ws.getCurrentTime());
		String itemName = !looks.isEmpty() ? looks.get(0).getDescription() : "the rattle";
		
		return CommandOutput.make("use")
			.put("success", true)
			.put("item", this.getKeyId())
			.put("item_name", itemName)
			.text(Markup.concat(
				Markup.raw("You shake "),
				Markup.em(itemName),
				Markup.raw(". It makes a pleasant rattling sound.")
			));
	}
}
