package com.benleskey.textengine.plugins;

import com.benleskey.textengine.CommandInput;
import com.benleskey.textengine.CommandOutput;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandVariant;

public class Echo extends Plugin {
	public static final String ECHO = "echo";
	public static final String M_ECHO_TEXT = "echo_text";

	public Echo(Game game) {
		super(game);
	}

	@Override
	public void activate() {
		game.registerCommand(new Command(ECHO, (c, i) -> c.sendOutput(CommandOutput.make(ECHO).text(i.get(M_ECHO_TEXT))),
				new CommandVariant(ECHO, "^echo[^\\w]*(.*)$", args -> CommandInput.makeNone().put(M_ECHO_TEXT, args.group(1)))));
	}
}
