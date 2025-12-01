package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.CommandCompletionSystem;
import com.benleskey.textengine.systems.CommandHelpSystem;
import com.benleskey.textengine.util.Markup;

import java.util.Collection;
import java.util.Map;

/**
 * HelpCommandPlugin provides the help command for listing and viewing
 * command help.
 */
public class HelpCommandPlugin extends Plugin implements OnPluginInitialize {

    public static final String HELP = "help";
    public static final String M_TOPIC = "topic";

    private CommandHelpSystem helpSystem;

    public HelpCommandPlugin(Game game) {
        super(game);
    }

    @Override
    public void onPluginInitialize() {
        helpSystem = game.getSystem(CommandHelpSystem.class);

        // Register help for the help command itself
        helpSystem.registerHelp("help [<topic>]",
                "Display help for a command. Without a topic, lists all available commands.");

        game.registerCommand(new Command(HELP, this::handleHelp,
                new CommandVariant("help_topic", "^help\\s+(.+?)\\s*$", args -> {
                    return CommandInput.makeNone().put(M_TOPIC, args.group(1).trim());
                }),
                new CommandVariant("help_list", "^help\\s*$", args -> CommandInput.makeNone())));

        // Register tab completion for help topics (command names)
        CommandCompletionSystem cc = game.getSystem(CommandCompletionSystem.class);
        cc.registerCompletionsForCommandToken(HELP, null,
                Map.of(1, () -> helpSystem.getCommandTokens()));
    }

    private void handleHelp(Client client, CommandInput input) {
        if (input.values.containsKey(M_TOPIC)) {
            handleHelpTopic(client, input.get(M_TOPIC));
        } else {
            handleHelpList(client);
        }
    }

    private void handleHelpTopic(Client client, String topic) {
        CommandHelpSystem.HelpMatch match = helpSystem.findBestMatch(topic);

        CommandOutput output = CommandOutput.make(HELP);

        if (match != null) {
            output.put(M_TOPIC, topic);
            output.text(Markup.escape(match.entry().getFullHelp()));
        } else {
            output.error("not_found");
            output.textf("No help found for: %s", Markup.escape(topic));
        }

        client.sendOutput(output);
    }

    private void handleHelpList(Client client) {
        Collection<CommandHelpSystem.HelpEntry> entries = helpSystem.getAllHelp();

        CommandOutput output = CommandOutput.make(HELP);

        StringBuilder sb = new StringBuilder();
        sb.append("Available commands:\n");

        for (CommandHelpSystem.HelpEntry entry : entries) {
            for (String syntax : entry.syntaxLines()) {
                sb.append("  ").append(syntax).append("\n");
            }
        }

        sb.append("\nType 'help <command>' for more information.");

        output.text(Markup.escape(sb.toString()));
        client.sendOutput(output);
    }
}
