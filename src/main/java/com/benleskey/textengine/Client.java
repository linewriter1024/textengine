package com.benleskey.textengine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Avatar;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.util.Markup;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Client {
	public static final String M_QUIT_FROM_SERVER = "quit_from_server";
	public static final String M_QUIT_FROM_CLIENT = "quit_from_client";
	public static final String M_CONTROLLING_ENTITY = "controlling_entity";
	public static final String M_ENTITY = "entity";

	public static final String M_CLEAR_ENTITY = "clear_entity";

	public static final String M_NO_ENTITY = "no_entity";

	public static CommandOutput NO_ENTITY = CommandOutput.make(M_NO_ENTITY)
			.text(Markup.escape("You are not connected to an in-world entity"));

	protected Game game;
	protected Optional<Avatar> entity;
	protected boolean alive;
	protected String id = "?";

	/**
	 * Client-specific mapping of numeric IDs to entities for disambiguation.
	 * Reset each time a command generates a new list (look, inventory, etc.).
	 * Allows users to type "1", "2", "3" instead of ambiguous keywords.
	 */
	protected Map<Integer, Entity> numericIdMap = new HashMap<>();

	/**
	 * Set the numeric ID mapping for this client.
	 * This allows users to reference entities by number when keywords are
	 * ambiguous.
	 */
	public void setNumericIdMap(Map<Integer, Entity> map) {
		this.numericIdMap = new HashMap<>(map);
	}

	/**
	 * Get an entity by its numeric ID, if mapped.
	 */
	public Optional<Entity> getEntityByNumericId(int numericId) {
		return Optional.ofNullable(numericIdMap.get(numericId));
	}

	/**
	 * Clear the numeric ID mapping.
	 */
	public void clearNumericIdMap() {
		this.numericIdMap.clear();
	}

	public abstract CommandInput waitForInput();

	public abstract void sendOutput(CommandOutput output);

	public abstract void sendStreamedOutput(CommandOutput output, Flow.Publisher<String> stream,
			CompletableFuture<String> future);

	public void quitFromServer() {
		if (alive) {
			alive = false;
			sendOutput(CommandOutput.make(M_QUIT_FROM_SERVER).text(Markup.escape("Goodbye.")));
			game.log.log("Disconnected %s", this);
		}
	}

	public void setEntity(Avatar newEntity) {
		this.entity = Optional.ofNullable(newEntity);
		this.entity.ifPresentOrElse(entity -> {
			sendOutput(CommandOutput.make(M_CONTROLLING_ENTITY).put(M_ENTITY, entity.getId()));
			game.log.log("%s is now controlling %s", this, entity);
		}, () -> {
			sendOutput(CommandOutput.make(M_CLEAR_ENTITY));
			game.log.log("%s is no longer controlling an entity", this);
		});
	}

	@Override
	public String toString() {
		return "Client#" + id;
	}
}
