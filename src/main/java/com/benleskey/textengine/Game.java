package com.benleskey.textengine;

import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.plugins.core.*;
import com.benleskey.textengine.systems.UniqueTypeSystem;
import com.benleskey.textengine.util.Interfaces;
import com.benleskey.textengine.util.Logger;
import lombok.Builder;
import lombok.Getter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;

public class Game {
	public static final String M_WELCOME = "welcome";
	public static final String M_VERSION = "version";
	private final Collection<Client> clients = new ArrayList<>();
	private final Map<String, Plugin> plugins = new LinkedHashMap<>();
	private final Map<Class<? extends PluginEvent>, List<Plugin>> pluginEventHandlers = new LinkedHashMap<>();
	private final Map<String, Command> commands = new LinkedHashMap<>();
	private final Map<String, GameSystem> systems = new LinkedHashMap<>();
	@Getter
	private final SchemaManager schemaManager;
	@Getter
	private UniqueTypeSystem uniqueTypeSystem;
	private final Connection databaseConnection;
	private final AtomicLong idCounter = new AtomicLong();
	public Logger log;
	public Logger errorLog = Logger.builder().stream(System.err).build();
	private boolean initialized = false;

	@Builder
	public Game(Logger log, Logger errorLog, Connection databaseConnection) {
		this.log = log;
		if (errorLog != null) {
			this.errorLog = errorLog;
		}
		this.databaseConnection = databaseConnection;

		log.log("%s", Version.toHumanString());

		schemaManager = new SchemaManager(this);

		registerPlugin(new CorePlugin(this));
		registerPlugin(new Echo(this));
		registerPlugin(new EntityPlugin(this));
		registerPlugin(new EventPlugin(this));
		registerPlugin(new InteractionPlugin(this));
		registerPlugin(new Quit(this));
		registerPlugin(new UnknownCommand(this));
		registerPlugin(new WorldPlugin(this));
	}

	public <T extends PluginEvent> void doPluginEvent(Class<T> pluginEvent, Consumer<T> runner) throws InternalException {
		for (Plugin plugin : pluginEventHandlers.getOrDefault(pluginEvent, Collections.emptyList())) {
			@SuppressWarnings("unchecked")
			T castPlugin = (T) plugin;
			runner.accept(castPlugin);
		}
	}

	public void initialize() throws InternalException {
		log.log("Initializing...");

		try {
			databaseConnection.setAutoCommit(false);
		} catch (SQLException autoCommitE) {
			throw new DatabaseException("Unable to configure database connection", autoCommitE);
		}

		try {
			log.log("Finishing plugin registration...");

			for(List<Plugin> plugins : pluginEventHandlers.values()) {
				plugins.sort(Comparator.comparing(Plugin::getEventOrder));
			}

			doPluginEvent(OnRegister.class, OnRegister::onRegister);

			log.log("Initializing schema...");

			schemaManager.initialize();

			log.log("Initializing plugins...");

			doPluginEvent(OnInitialize.class, OnInitialize::onInitialize);

			uniqueTypeSystem = this.getSystem(UniqueTypeSystem.class);

			log.log("Initializing systems...");

			for (GameSystem system : systems.values()) {
				int previousVersion = system.getSchema().getVersionNumber();
				system.initialize();
				int nextVersion = system.getSchema().getVersionNumber();
				if (previousVersion == nextVersion) {
					log.log("System %s version %d", system.getId(), nextVersion);
				} else if (previousVersion == 0) {
					log.log("System %s initialized to version %d", system.getId(), nextVersion);
				} else {
					log.log("System %s upgraded from version %d to version %d", system.getId(), previousVersion, nextVersion);
				}
			}

			log.log("Systems ready...");

			doPluginEvent(OnCoreSystemsReady.class, OnCoreSystemsReady::onCoreSystemsReady);

			log.log("Starting game...");

			doPluginEvent(OnStart.class, OnStart::onStart);

			try {
				log.log("Committing initialization...");
				databaseConnection.commit();
			} catch (SQLException commitE) {
				throw new DatabaseException("Unable to commit initialization transaction", commitE);
			}
		} catch (Throwable e) {
			try {
				databaseConnection.rollback();
			} catch (SQLException rollbackE) {
				errorLog.log("Unable to rollback initialization transaction: " + rollbackE);
				rollbackE.printStackTrace(errorLog.getStream());
			}
			throw e;
		}

		initialized = true;

		log.log("Initialized.");
	}

	@SuppressWarnings("unchecked")
	public <T> T getSystem(String name) {
		return Optional.ofNullable((T) systems.getOrDefault(name, null)).orElseThrow();
	}

	public <T extends SingletonGameSystem> T getSystem(Class<T> c) {
		return getSystem(SingletonGameSystem.getSingletonGameSystemId(c));
	}

	public Connection db() {
		return databaseConnection;
	}

	public long getNewGlobalId() throws DatabaseException {
		return schemaManager.getNewId();
	}

	public long getNewSessionId() {
		return idCounter.incrementAndGet();
	}

	public void registerPlugin(Plugin plugin) {
		plugins.put(plugin.getId(), plugin);

		Set<Class<? extends PluginEvent>> events = new HashSet<>();
		for(Class<?> event : Interfaces.getAllInterfaces(plugin.getClass())) {
			if(PluginEvent.class.isAssignableFrom(event)) {
				@SuppressWarnings("unchecked")
				Class<? extends PluginEvent> castEvent = (Class<? extends PluginEvent>) event;
				events.add(castEvent);
				pluginEventHandlers.compute(castEvent, (k, v) -> {
					List<Plugin> handlers = v;
					if(handlers == null) {
						handlers = new ArrayList<>();
					}
					handlers.add(plugin);
					return handlers;
				});
			}
		}

		log.log("Registered plugin %s with event handlers [%s]", plugin.getId(), String.join(", ", events.stream().map(Class::getSimpleName).sorted().toList()));
	}

	public void registerCommand(Command command) {
		log.log("Registering command %s (variants: %d)", command.getName(), command.getVariants().size());
		commands.put(command.getName(), command);
	}

	public void registerClient(Client client) throws InternalException {
		client.setAlive(true);
		client.setId(String.valueOf(getNewSessionId()));
		log.log("Registering client: %s", client);
		clients.add(client);
		client.sendOutput(CommandOutput.make(M_WELCOME).put(M_VERSION, Version.toMessage()).textf("Welcome to %s %s <%s>", Version.humanName, Version.versionNumber, Version.url));
		doPluginEvent(OnStartClient.class, plugin -> plugin.onStartClient(client));
	}

	public <T extends GameSystem> T registerSystem(T system) {
		log.log("Registering system: %s", system.getId());
		systems.put(system.getId(), system);
		return system;
	}

	private boolean anyClientAlive() {
		return clients.stream().anyMatch(Client::isAlive);
	}

	public void loopWithClients() throws InternalException {
		if (!initialized) {
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
				} catch (SQLException e) {
					throw new DatabaseException("Unable to commit loop transaction", e);
				}
			} catch (Throwable e) {
				try {
					databaseConnection.rollback();
				} catch (SQLException rollbackE) {
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

	public void feedCommand(Client client, CommandInput input) throws InternalException {
		if (commands.containsKey(input.getId())) {
			commands.get(input.getId()).getFunction().run(client, input);
		} else {
			CommandInput unknown = CommandInput.make(CommandOutput.M_UNKNOWN_COMMAND).put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_COMMAND, input);
			input.getLine().ifPresent(line -> unknown.put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_LINE, line));
			feedCommand(client, unknown);
		}
	}

	public Plugin getPlugin(Class<? extends Plugin> plugin) {
		return plugins.get(plugin.getCanonicalName());
	}
}
