package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.*;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandFunction;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.util.Message;

import java.util.*;
import java.util.stream.Collectors;

public class InteractionPlugin extends Plugin {
	public static final String LOOK = "look";
	public static final String LOOK_WITHOUT_ARGUMENTS = "look_without_arguments";
	public static final String M_LOOK = "look";
	public static final String M_LOOK_ENTITIES = "look_entities";
	public static final String M_LOOK_ENTITY_LOOKS = "look_entity_looks";
	public static final String M_LOOK_TYPE = "look_type";
	public static final String M_LOOK_DESCRIPTION = "look_description";

	public InteractionPlugin(Game game) {
		super(game);
	}

	private CommandOutput buildLookOutput(Map<Entity, List<LookDescriptor>> groupedLooks) {
		CommandOutput output = CommandOutput.make(M_LOOK);
		StringJoiner overallText = new StringJoiner("\n");

		Message entities = Message.make();
		for (Entity entity : groupedLooks.keySet()) {

			Message entityMessage = Message.make();
			Message entityLooks = Message.make();
			StringJoiner entityText = new StringJoiner(", ");

			for(LookDescriptor lookDescriptor : groupedLooks.get(entity)) {
				Message lookMessage = Message.make()
						.put(M_LOOK_TYPE, lookDescriptor.getType())
						.put(M_LOOK_DESCRIPTION, lookDescriptor.getDescription());
				entityLooks.put(lookDescriptor.getLook().getKeyId(), lookMessage);
				entityText.add(lookDescriptor.getDescription());
			}

			entityMessage.put(M_LOOK_ENTITY_LOOKS, entityLooks);

			overallText.add(entityText.toString());
			entities.put(entity.getKeyId(), entityMessage);
		}

		output.text(overallText.toString());
		output.put(M_LOOK_ENTITIES, entities);

		return output;
	}

	@Override
	public void initialize() {
		game.registerCommand(new Command(LOOK, (client, input) -> {
			Entity entity = client.getEntity().orElse(null);
			if(entity != null) {
				LookSystem ls = game.getSystem(LookSystem.class);
				client.sendOutput(buildLookOutput(ls.getSeenLooks(entity).stream().collect(Collectors.groupingBy(ld -> ld.getEntity()))));
			}
			else {
				client.sendOutput(Client.NO_ENTITY);
			}
		}, new CommandVariant(LOOK_WITHOUT_ARGUMENTS, "^look([^\\w]+|$)", args -> CommandInput.makeNone())));
	}
}
