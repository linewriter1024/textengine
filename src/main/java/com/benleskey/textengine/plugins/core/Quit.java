package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.CommandHelpSystem;

public class Quit extends Plugin implements OnPluginInitialize {
	public Quit(Game game) {
		super(game);
	}

	@Override
	public void onPluginInitialize() {
		// Register help
		CommandHelpSystem helpSystem = game.getSystem(CommandHelpSystem.class);
		helpSystem.registerHelp("quit", "Quit the game.");

		game.registerCommand(new Command(Client.M_QUIT_FROM_CLIENT, (c, i) -> c.quitFromServer(),
				new CommandVariant(Client.M_QUIT_FROM_CLIENT, "^quit([^\\w]+|$)", args -> CommandInput.makeNone())));
	}
}
