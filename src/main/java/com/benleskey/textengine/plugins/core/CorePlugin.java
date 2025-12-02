package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.CommandHelpSystem;
import com.benleskey.textengine.systems.SpatialSystem;
import com.benleskey.textengine.systems.TickSystem;
import com.benleskey.textengine.systems.UniqueTypeSystem;
import com.benleskey.textengine.systems.CommandCompletionSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

public class CorePlugin extends Plugin implements OnPluginInitialize {

	public static final String SEED = "seed";
	public static final String M_SEED = "seed";

	public CorePlugin(Game game) {
		super(game);
	}

	@Override
	public void onPluginInitialize() {
		game.registerSystem(new UniqueTypeSystem(game));
		game.registerSystem(new CommandHelpSystem(game));
		game.registerSystem(new CommandCompletionSystem(game));
		game.registerSystem(new WorldSystem(game));
		game.registerSystem(new TickSystem(game));
		game.registerSystem(new SpatialSystem(game));

		// Register seed command
		CommandHelpSystem helpSystem = game.getSystem(CommandHelpSystem.class);
		helpSystem.registerHelp("seed", "Display the world generation seed.");
		game.registerCommand(new Command(SEED, (c, i) -> {
			WorldSystem ws = game.getSystem(WorldSystem.class);
			long seed = ws.getSeed();
			c.sendOutput(CommandOutput.make(SEED)
					.put(M_SEED, seed)
					.text(Markup.escape(String.format("World seed: %d", seed))));
		}, new CommandVariant(SEED, "^seed\\s*$", args -> CommandInput.makeNone())));
	}
}
