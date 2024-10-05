package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandVariant;

public class Quit extends Plugin {
	public Quit(Game game) {
		super(game);
	}

	@Override
	public void initialize() {
		game.registerCommand(new Command(Client.M_QUIT_FROM_CLIENT, (c, i) -> c.quitFromServer(),
			new CommandVariant(Client.M_QUIT_FROM_CLIENT, "^quit([^\\w]+|$)", args -> CommandInput.makeNone())));
	}
}
