package com.benleskey.textengine;

import com.benleskey.textengine.model.Entity;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

@Getter
@Setter
public abstract class Client {
	public static final String M_QUIT_FROM_SERVER = "quit_from_server";
	public static final String M_QUIT_FROM_CLIENT = "quit_from_client";
	public static final String M_CONTROLLING_ENTITY = "controlling_entity";
	public static final String M_ENTITY = "entity";

	public static final String M_CLEAR_ENTITY = "clear_entity";

	public static final String M_NO_ENTITY = "no_entity";

	public static CommandOutput NO_ENTITY = CommandOutput.make(M_NO_ENTITY).text("You are not connected to an in-world entity");

	protected Game game;
	protected Optional<Entity> entity;
	protected boolean alive;
	protected String id = "?";

	public abstract CommandInput waitForInput();

	public abstract void sendOutput(CommandOutput output);

	public void quitFromServer() {
		if (alive) {
			alive = false;
			sendOutput(CommandOutput.make(M_QUIT_FROM_SERVER).text("Goodbye."));
			game.log.log("Disconnected %s", this);
		}
	}

	public void setEntity(Entity newEntity) {
		this.entity = Optional.of(newEntity);
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
