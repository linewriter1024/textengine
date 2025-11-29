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
    public static final String DEBUG_GENERATE_NAME_VARIANT = "debug:generatename_variant";
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
                new CommandVariant(DEBUG_GENERATE_NAME_VARIANT,
                        "^(?:debug:generatename)\\s+([^\\s]+)\\s+([0-9]+)\\s*$",
                        this::parseDebugGenerateName)));

    }

    @Override
    public void onCoreSystemsReady() {
        this.nameGenerationSystem = game.getSystem(NameGenerationSystem.class);
    }

    private CommandInput parseDebugGenerateName(java.util.regex.Matcher matcher) {
        String style = matcher.group(1).trim().toLowerCase();
        int qty = Integer.parseInt(matcher.group(2));
        return CommandInput.make(DEBUG_GENERATE_NAME).put(M_DEBUG_GENERATE_STYLE, style).put(M_DEBUG_GENERATE_QTY, qty);
    }

    private void handleDebugGenerateName(Client client, CommandInput input) {
        if (nameGenerationSystem == null) {
            client.sendOutput(CommandOutput.make(DEBUG_GENERATE_NAME).text(Markup.escape("Name generation system not ready")));
            return;
        }

        String style = input.<String>getO(M_DEBUG_GENERATE_STYLE).orElse("default");
        int qty = input.<Integer>getO(M_DEBUG_GENERATE_QTY).orElse(1);

        
        if (qty <= 0) {
            client.sendOutput(CommandOutput.make(DEBUG_GENERATE_NAME).error("invalid_quantity").text(Markup.escape("Quantity must be > 0")));
            return;
        }

        if (nameGenerationSystem.getStyle(style).isEmpty()) {
            java.util.Set<String> regs = nameGenerationSystem.getRegisteredStyles();
            client.sendOutput(CommandOutput.make(DEBUG_GENERATE_NAME).error("unknown_style").text(Markup.escape("Unknown style: " + style + ". Registered styles: " + regs)));
            return;
        }

        java.util.Random r = new java.util.Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < qty; i++) {
            String n = nameGenerationSystem.generateName(style, r);
            sb.append(n);
            if (i < qty - 1) sb.append('\n');
        }
        client.sendOutput(CommandOutput.make(DEBUG_GENERATE_NAME).text(Markup.escape(sb.toString())));
    }
}
