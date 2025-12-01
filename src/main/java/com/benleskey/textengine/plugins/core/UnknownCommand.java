package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.CommandHelpSystem;
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
				output.put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_LINE, line);

				// Try to find a matching help entry
				CommandHelpSystem helpSystem = game.getSystem(CommandHelpSystem.class);
				CommandHelpSystem.HelpMatch match = helpSystem.findBestMatch(line);

				if (match != null) {
					if (match.exactMatch()) {
						// Command is correct but syntax doesn't match any variant
						output.textf("Invalid syntax: %s\nUsage:\n  %s",
								Markup.escape(line),
								Markup.escape(match.entry().getSyntaxDisplay().replace("\n", "\n  ")));
					} else {
						// Typo in command name
						output.textf("Unknown command: %s\nDid you mean:\n  %s",
								Markup.escape(line),
								Markup.escape(match.entry().getSyntaxDisplay().replace("\n", "\n  ")));
					}
				} else {
					output.textf("Unknown command: %s", Markup.escape(line));
				}
			}, () -> output.text(Markup.escape("Unknown command")));
			c.sendOutput(output);
		}));
	}
}
