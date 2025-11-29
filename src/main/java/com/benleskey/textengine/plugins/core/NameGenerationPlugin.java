package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.hooks.core.OnCoreSystemsReady;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.NameGenerationSystem;
import com.benleskey.textengine.systems.CommandCompletionSystem;
import com.benleskey.textengine.util.Markup;

/**
 * NameGenerationPlugin
 *
 * Registers the NameGenerationSystem and a convenience CLI command
 * for name generation. The plugin also demonstrates sample style
 * registrations (default, elvish, orcish) used by the generator.
 */
public class NameGenerationPlugin extends Plugin implements OnPluginInitialize, OnCoreSystemsReady {

    public static final String DEBUG_GENERATE_NAME = "debug:generatename";
    public static final String M_DEBUG_GENERATE_STYLE = "style";
    public static final String M_DEBUG_GENERATE_QTY = "quantity";

    private NameGenerationSystem nameGenerationSystem;

    public NameGenerationPlugin(Game game) {
        super(game);
    }

    @Override
    public void onPluginInitialize() {
        game.registerSystem(new NameGenerationSystem(game));

        game.registerCommand(new Command(DEBUG_GENERATE_NAME, this::handleDebugGenerateName,
                new CommandVariant(DEBUG_GENERATE_NAME,
                        "^(?:debug:generatename)(?:\\s+([^\\s]+))?(?:\\s+([0-9]+))?\\s*$",
                        this::parseDebugGenerateName)));

    }

    @Override
    public void onCoreSystemsReady() {
        this.nameGenerationSystem = game.getSystem(NameGenerationSystem.class);

        CommandCompletionSystem cc = game.getSystem(CommandCompletionSystem.class);
        cc.registerCompletionsForCommandToken(DEBUG_GENERATE_NAME, null,
                java.util.Map.of(1, nameGenerationSystem::getRegisteredStyles));
    }

    private CommandInput parseDebugGenerateName(java.util.regex.Matcher matcher) {
        String style = null;
        if (matcher.group(1) != null) {
            style = matcher.group(1).trim().toLowerCase();
        }
        Integer qty = null;
        if (matcher.group(2) != null) {
            qty = Integer.valueOf(matcher.group(2));
        }
        CommandInput in = CommandInput.make(DEBUG_GENERATE_NAME);
        if (style != null)
            in.put(M_DEBUG_GENERATE_STYLE, style);
        if (qty != null)
            in.put(M_DEBUG_GENERATE_QTY, qty);
        return in;
    }

    private void handleDebugGenerateName(Client client, CommandInput input) {
        var styleOpt = input.<String>getO(M_DEBUG_GENERATE_STYLE);
        String style = styleOpt.orElse(null);
        int qty = input.<Integer>getO(M_DEBUG_GENERATE_QTY).orElse(1);

        java.util.Random r = new java.util.Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < qty; i++) {
            String n = nameGenerationSystem.generateName(style, r);
            sb.append(n);
            if (i < qty - 1)
                sb.append('\n');
        }
        client.sendOutput(CommandOutput.make(DEBUG_GENERATE_NAME).text(Markup.escape(sb.toString())));
    }
}
