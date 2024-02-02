package com.benleskey.textengine;

import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.plugins.core.Echo;
import com.benleskey.textengine.plugins.core.EntityPlugin;
import com.benleskey.textengine.plugins.core.Quit;
import com.benleskey.textengine.plugins.core.UnknownCommand;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.util.Logger;
import lombok.Builder;
import lombok.Data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

public class Game {
	public static final String M_WELCOME = "welcome";
	public static final String M_VERSION = "version";

	public Logger log;
	@Builder.Default
	public Logger errorLog = Logger.builder().stream(System.err).build();
	private final Collection<Client> clients = new ArrayList<>();
	private final Map<String, Plugin> plugins = new HashMap<>();
	private final Map<String, Command> commands = new HashMap<>();
	private final Map<String, GameSystem> systems = new HashMap<>();

	private final SchemaManager schemaManager;

	private final Connection databaseConnection;

	private boolean initialized = false;

	@Builder
	public Game(Logger log, Logger errorLog, Connection databaseConnection) {
		this.log = log;
		this.errorLog = errorLog;
		this.databaseConnection = databaseConnection;

		log.log("%s", Version.toHumanString());

		schemaManager = new SchemaManager(this);

		registerPlugin(new UnknownCommand(this));
		registerPlugin(new Quit(this));
		registerPlugin(new Echo(this));

		registerPlugin(new EntityPlugin(this));
	}

	public void initialize() throws DatabaseException {
		log.log("Initializing...");

		try {
			databaseConnection.setAutoCommit(false);
		}
		catch(SQLException autoCommitE) {
			throw new DatabaseException("Unable to configure database connection", autoCommitE);
		}

		try {
			schemaManager.initialize();

			for(Plugin plugin : plugins.values()) {
				plugin.initialize();
			}

			for(GameSystem system : systems.values()) {
				int previousVersion = system.getSchema().getVersionNumber();
				system.initialize();
				int nextVersion = system.getSchema().getVersionNumber();
				if(previousVersion == nextVersion) {
					log.log("System %s version %d", system.getId(), nextVersion);
				}
				else if(previousVersion == 0) {
					log.log("System %s initialized to version %d", system.getId(), nextVersion);
				}
				else {
					log.log("System %s upgraded from version %d to version %d", system.getId(), previousVersion, nextVersion);
				}
			}

			try {
				databaseConnection.commit();
			}
			catch(SQLException commitE) {
				throw new DatabaseException("Unable to commit initialization transaction", commitE);
			}
		}
		catch(Throwable e) {
			try {
				databaseConnection.rollback();
			}
			catch(SQLException rollbackE) {
				errorLog.log("Unable to rollback initialization transaction: " + rollbackE);
				rollbackE.printStackTrace(errorLog.getStream());
			}
			throw e;
		}

		initialized = true;

		log.log("Initialized.");
	}

	public Connection db() {
		return databaseConnection;
	}

	public long getNewId() throws DatabaseException {
		return schemaManager.getNewId();
	}

	public SchemaManager getSchemaManager() {
		return schemaManager;
	}

	public void registerPlugin(Plugin plugin) {
		plugins.put(plugin.getId(), plugin);
		log.log("Registered plugin %s", plugin.getId());
	}

	public void registerCommand(Command command) {
		log.log("Registering command %s (variants: %d)", command.getName(), command.getVariants().size());
		commands.put(command.getName(), command);
	}

	public void registerClient(Client client) throws InternalException {
		client.setAlive(true);
		client.setId(String.valueOf(getNewId()));
		log.log("Registering client: %s", client);
		clients.add(client);
		client.sendOutput(CommandOutput.make(M_WELCOME).put(M_VERSION, Version.toMessage()).textf("Welcome to %s %s <%s>", Version.humanName, Version.versionNumber, Version.url));
	}

	public void registerSystem(GameSystem system) {
		log.log("Registering system: %s", system.getId());
		systems.put(system.getId(), system);
	}

	private boolean anyClientAlive() {
		return clients.stream().anyMatch(Client::isAlive);
	}

	public void loopWithClients() throws InternalException {
		if(!initialized) {
			throw new IllegalStateException("Tried to run the game without calling initialize() first");
		}
		log.log("Looping with clients...");
		while (anyClientAlive()) {
			try {
				for (Client client : clients) {
					feedCommand(client, client.waitForInput());
				}
				try {
					databaseConnection.commit();
				}
				catch(SQLException e) {
					throw new DatabaseException("Unable to commit loop transaction", e);
				}
			}
			catch(Throwable e) {
				try {
					databaseConnection.rollback();
				}
				catch(SQLException rollbackE) {
					errorLog.log("Unable to rollback loop transaction: " + rollbackE);
					rollbackE.printStackTrace(errorLog.getStream());
				}
				throw e;
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
