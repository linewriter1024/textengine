package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.util.Markup;

public class UnknownCommand extends Plugin implements OnPluginInitialize {
	public UnknownCommand(Game game) {
		super(game);
	}

	@Override
	public void onPluginInitialize() {
		game.registerCommand(new Command(CommandOutput.M_UNKNOWN_COMMAND, (c, i) -> {
			CommandOutput output = CommandOutput.make(CommandOutput.M_UNKNOWN_COMMAND);
			i.<CommandInput>getO(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_COMMAND)
					.ifPresent(original -> output.put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_COMMAND, original));
			i.<String>getO(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_LINE).ifPresentOrElse(line -> {
				output.textf("Unknown command: %s", line);
				output.put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_LINE, line);
			}, () -> output.text(Markup.escape("Unknown command")));
			c.sendOutput(output);
		}));
	}
}
