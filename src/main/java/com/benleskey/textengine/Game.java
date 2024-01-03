package com.benleskey.textengine;

import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.plugins.Echo;
import com.benleskey.textengine.plugins.Quit;
import com.benleskey.textengine.plugins.UnknownCommand;
import com.benleskey.textengine.util.Logger;
import lombok.Builder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

public class Game implements AutoCloseable {
	public static final String M_WELCOME = "welcome";
	public static final String M_VERSION = "version";

	private final Logger log;
	@Builder.Default
	private final Logger errorLog = Logger.builder().stream(System.err).build();
	private final Collection<Client> clients = new ArrayList<>();
	private final Map<String, Plugin> plugins = new HashMap<>();
	private final Map<String, Command> commands = new HashMap<>();
	private long idCounter = 1;

	private final Connection databaseConnection;

	@Builder
	public Game(Logger log, Logger errorLog) throws DatabaseException {
		this.log = log;

		log.log("%s", Version.toHumanString());

		try {
			databaseConnection = DriverManager.getConnection("jdbc:sqlite::memory:");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to connect to database", e);
		}

		registerPlugin(new UnknownCommand(this));
		registerPlugin(new Quit(this));
		registerPlugin(new Echo(this));
	}

	private Connection getConnection() {
		return databaseConnection;
	}

	public void close() {
		try {
			if (databaseConnection != null) {
				databaseConnection.close();
			}
		} catch (SQLException e) {
			errorLog.log("Unable to close database connection: " + e);
			e.printStackTrace(errorLog.getStream());
		}
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
		for (Command command : commands.values()) {
			for (CommandVariant variant : command.getVariants().values()) {
				Matcher matcher = variant.getRegex().matcher(line);
				if (matcher.find()) {
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
		if (commands.containsKey(input.getId())) {
			commands.get(input.getId()).getFunction().run(client, input);
		} else {
			CommandInput unknown = CommandInput.make(CommandOutput.M_UNKNOWN_COMMAND).put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_COMMAND, input);
			input.getLine().ifPresent(line -> unknown.put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_LINE, line));
			feedCommand(client, unknown);
		}
	}
}
