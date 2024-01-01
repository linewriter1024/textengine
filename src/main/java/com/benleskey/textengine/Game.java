package com.benleskey.textengine;

import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.plugins.Echo;
import com.benleskey.textengine.plugins.Quit;
import com.benleskey.textengine.plugins.UnknownCommand;
import com.benleskey.textengine.util.Logger;
import lombok.Builder;

import java.util.*;
import java.util.regex.Matcher;

public class Game {
	public static final String M_WELCOME = "welcome";
	public static final String M_VERSION = "version";

	private Logger log;
	private Collection<Client> clients = new ArrayList<>();
	private Map<String, Plugin> plugins = new HashMap<>();
	private Map<String, Command> commands = new HashMap<>();
	private long idCounter = 1;

	@Builder
	public Game(Logger log) {
		this.log = log;

		log.log("Loading %s", Version.toHumanString());

		registerPlugin(new UnknownCommand(this));
		registerPlugin(new Quit(this));
		registerPlugin(new Echo(this));
	}

	public void registerPlugin(Plugin plugin) {
		plugins.put(plugin.getId(), plugin);
		log.log("Registered plugin %s", plugin.getId());
		plugin.activate();
	}

	public void registerCommand(Command command) {
		log.log("Registering command %s (variants: %d)", command.getName(), command.getVariants().size());
		commands.put(command.getName(), command);
	}

	public void registerClient(Client client) {
		client.setAlive(true);
		client.setId(String.valueOf(idCounter++));
		log.log("Registering client: %s", client);
		clients.add(client);
		client.sendOutput(CommandOutput.make(M_WELCOME).put(M_VERSION, Version.toMessage()).textf("Welcome to %s %s <%s>", Version.humanName, Version.versionNumber, Version.url));
	}

	private boolean anyClientAlive() {
		return clients.stream().anyMatch(Client::isAlive);
	}

	public void loopWithClients() {
		log.log("Looping with clients...");
		while (anyClientAlive()) {
			for (Client client : clients) {
				feedCommand(client, client.waitForInput());
			}
		}
		log.log("No clients left alive...");
	}

	public CommandInput inputLineToCommandInput(String line) {
		for(Command command : commands.values()) {
			for(CommandVariant variant : command.getVariants().values()) {
				Matcher matcher = variant.getRegex().matcher(line);
				if(matcher.find()) {
					CommandInput processedInput = variant.getFunction().run(matcher);
					processedInput.id(command.getName());
					processedInput.line(line);
					return processedInput;
				}
			}
		}

		return CommandInput.make(CommandOutput.M_UNKNOWN_COMMAND).put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_LINE, line);
	}

	public void feedCommand(Client client, CommandInput input) {
		log.log("Command input (%s): %s", client, input.toPrettyString());

		if(commands.containsKey(input.getId())) {
			commands.get(input.getId()).getFunction().run(client, input);
		}
		else {
			CommandInput unknown = CommandInput.make(CommandOutput.M_UNKNOWN_COMMAND).put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_COMMAND, input);
			input.getLine().ifPresent(line -> unknown.put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_LINE, line));
			feedCommand(client, unknown);
		}
	}
}
