package com.benleskey.textengine;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Client {
	public static final String M_QUIT_FROM_SERVER = "quit_from_server";
	public static final String M_QUIT_FROM_CLIENT = "quit_from_client";

	protected Game game;
	protected Entity entity;
	protected boolean alive;
	protected String id = "?";

	public abstract CommandInput waitForInput();

	public abstract void sendOutput(CommandOutput output);

	public void quitFromServer() {
		if (alive) {
			alive = false;
			sendOutput(CommandOutput.make(M_QUIT_FROM_SERVER).text("Goodbye."));
		}
	}

	@Override
	public String toString() {
		return "Client " + id;
	}
}
