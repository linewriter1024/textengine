package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.NameGenerationSystem;
import com.benleskey.textengine.Client;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.util.Markup;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;

/**
 * NameGenerationPlugin
 *
 * Registers the NameGenerationSystem and a convenience CLI command
 * for name generation. The plugin also demonstrates sample style
 * registrations (default, elvish, orcish) used by the generator.
 */
public class NameGenerationPlugin extends Plugin implements OnPluginInitialize {
    public static final String STYLE_DEFAULT = "default";
    public static final String STYLE_ELVISH = "elvish";
    public static final String STYLE_ORCISH = "orcish";

    public NameGenerationPlugin(Game game) {
        super(game);
    }

    @Override
    public void onPluginInitialize() {
        game.registerSystem(new NameGenerationSystem(game));

        game.registerCommand(new Command(GENERATE_NAME, this::handleGenerateName,

                new CommandVariant(GENERATE_NAME,
                        "^(?:generatename|namegen|genname)(?:\\s+(\\S+))?(?:\\s+(\\d+))?\\s*$",
                        this::parseGenerateName)));

        NameGenerationSystem ngs = game.getSystem(NameGenerationSystem.class);

        ngs.registerStyle(STYLE_ORCISH,
                List.of("ug", "g", "ruk", "mog", "grom", "kr", "gar", "zug", "mog", "gorn", "rag", "ruk", "urk"));

        ngs.registerStyle(STYLE_ELVISH, List.of("ae", "eth", "riel", "loth", "ion", "mir", "elen", "sil", "wyn", "lae",
                "al", "ar", "iel", "anya", "wyn"));

        ngs.registerStyle(STYLE_DEFAULT, List.of("al", "an", "ar", "bel", "dor", "el", "en", "er", "gal", "gorn", "hel",
                "is", "or", "ul", "ur", "ri", "ta", "ven", "my", "sha", "ka", "lo", "mi", "za", "ri", "sa", "te"));
    }

    public static final String GENERATE_NAME = "generatename";
    public static final String M_GENERATE_NAME_STYLE = "style";
    public static final String M_GENERATE_NAME_QTY = "quantity";
    public static final String M_GENERATED_NAME = "generated_name";

    private CommandInput parseGenerateName(Matcher matcher) {
        String style = matcher.group(1) == null ? "" : matcher.group(1).trim();
        String qtyStr = matcher.group(2);
        int qty = 1;
        if (qtyStr != null) {
            try {
                qty = Integer.parseInt(qtyStr);
            } catch (NumberFormatException e) {
                qty = 1;
            }
        }
        if (qty < 1)
            qty = 1;

        return CommandInput.makeNone().put(M_GENERATE_NAME_STYLE, style).put(M_GENERATE_NAME_QTY, qty);
    }

    private void handleGenerateName(Client client, CommandInput input)
            throws com.benleskey.textengine.exceptions.InternalException {
        String style = input.<String>getO(M_GENERATE_NAME_STYLE).orElse(STYLE_DEFAULT);
        int qty = input.<Integer>getO(M_GENERATE_NAME_QTY).orElse(1);

        NameGenerationSystem ngs = game.getSystem(NameGenerationSystem.class);

        if (!ngs.getRegisteredStyles().contains(style)) {
            client.sendOutput(CommandOutput.make(GENERATE_NAME).error(
                    String.format("Unknown style: %s. Registered styles: %s", style, ngs.getRegisteredStyles())));
            return;
        }
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < qty; i++) {
            String generatedName = ngs.generateName(style, random);
            if (i > 0)
                sb.append(", ");
            sb.append(generatedName);
        }
        String out = sb.toString();
        client.sendOutput(CommandOutput.make(GENERATE_NAME)
                .put(M_GENERATED_NAME, out)
                .text(Markup.escape(String.format("Generated name(s) (style=%s): %s", style, out))));
    }
}
