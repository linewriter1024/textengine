package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;

public class Echo extends Plugin implements OnPluginInitialize {
	public static final String ECHO = "echo";
	public static final String M_ECHO_TEXT = "echo_text";

	public Echo(Game game) {
		super(game);
	}

	@Override
	public void onPluginInitialize() {
		game.registerCommand(new Command(ECHO, (c, i) -> c.sendOutput(CommandOutput.make(ECHO).text(i.get(M_ECHO_TEXT))),
			new CommandVariant(ECHO, "^echo[^\\w]*(.*)$", args -> CommandInput.makeNone().put(M_ECHO_TEXT, args.group(1)))));
	}
}
