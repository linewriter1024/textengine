package com.benleskey.textengine;

import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.*;
import com.benleskey.textengine.plugins.core.*;
import com.benleskey.textengine.systems.UniqueTypeSystem;
import com.benleskey.textengine.util.*;
import lombok.Builder;
import lombok.Getter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

public class Game {
	public static final int CACHE_SIZE = 2000;
	public static final String M_WELCOME = "welcome";
	public static final String M_VERSION = "version";
	private final Collection<Client> clients = new ArrayList<>();
	private final Map<String, Plugin> plugins = new LinkedHashMap<>();
	private final Map<String, Plugin> tentativePlugins = new LinkedHashMap<>();
	private final HookManager<HookHandler> hooks = new HookManager<>();
	private final Map<String, Command> commands = new LinkedHashMap<>();
	private final Map<String, GameSystem> systems = new LinkedHashMap<>();
	@Getter
	private final SchemaManager schemaManager;
	@Getter
	private UniqueTypeSystem uniqueTypeSystem;
	private final Connection databaseConnection;
	private final Long seed;
	private final AtomicLong idCounter = new AtomicLong();
	public Logger log;
	public Logger errorLog = Logger.builder().stream(System.err).build();
	private boolean initialized = false;

	@Builder
	public Game(Logger log, Logger errorLog, Connection databaseConnection, Long seed) {
		this.log = log;
		if (errorLog != null) {
			this.errorLog = errorLog;
		}
		this.databaseConnection = databaseConnection;
		this.seed = seed;

		log.log("%s", Version.toHumanString());

		schemaManager = new SchemaManager(this);

		registerPlugin(new CorePlugin(this));
		registerPlugin(new Echo(this));
		registerPlugin(new NameGenerationPlugin(this));
		registerPlugin(new EntityPlugin(this));
		registerPlugin(new EventPlugin(this));
		registerPlugin(new InteractionPlugin(this));
		registerPlugin(new ItemInteractionPlugin(this));
		registerPlugin(new NavigationPlugin(this));
		registerPlugin(new WaitCommandPlugin(this));
		registerPlugin(new DicePlugin(this));
		registerPlugin(new Quit(this));
		registerPlugin(new UnknownCommand(this));

		// Register tentative plugins (activated only if needed as dependencies)
		registerTentativePlugin(new com.benleskey.textengine.plugins.procgen1.ProceduralWorldPlugin(this, seed));
	}

	public void initialize() throws InternalException {
		log.log("Initializing...");

		try {
			databaseConnection.setAutoCommit(false);
		} catch (SQLException autoCommitE) {
			throw new DatabaseException("Unable to configure database connection", autoCommitE);
		}

		try {
			// Activate tentative plugins that are needed as dependencies
			activateTentativePlugins();

			hooks.calculateOrder();

			for (Plugin plugin : plugins.values().stream().sorted(Comparator.comparing(Plugin::getEventOrder))
					.toList()) {
				log.log("Plugin %s event order %d", plugin.getId(), plugin.getEventOrder());
			}

			log.log("Initializing schema...");

			schemaManager.initialize();

			log.log("Initializing plugins...");

			hooks.doEvent(OnPluginInitialize.class, OnPluginInitialize::onPluginInitialize);

			uniqueTypeSystem = this.getSystem(UniqueTypeSystem.class);

			log.log("Initializing systems...");

			hooks.calculateOrder();

			hooks.doEvent(OnSystemInitialize.class, system -> {
				int previousVersion = system.getSchema().getVersionNumber();

				system.onSystemInitialize();

				int nextVersion = system.getSchema().getVersionNumber();

				if (previousVersion == nextVersion) {
					log.log("System %s (order %d) version %d", system.getId(), system.getEventOrder(), nextVersion);
				} else if (previousVersion == 0) {
					log.log("System %s (order %d) initialized to version %d", system.getId(), system.getEventOrder(),
							nextVersion);
				} else {
					log.log("System %s (order %d) upgraded from version %d to version %d", system.getId(),
							system.getEventOrder(), previousVersion, nextVersion);
				}
			});

			log.log("Systems ready...");

			hooks.doEvent(OnCoreSystemsReady.class, OnCoreSystemsReady::onCoreSystemsReady);

			log.log("Entity types registered...");

			hooks.doEvent(OnEntityTypesRegistered.class, OnEntityTypesRegistered::onEntityTypesRegistered);

			log.log("Starting game...");

			hooks.doEvent(OnStart.class, OnStart::onStart);

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

		Set<Class<? extends HookEvent>> events = hooks.registerHookHandler(plugin);

		if (plugin instanceof OnPluginRegister onPluginRegister) {
			onPluginRegister.onPluginRegister();
		}

		log.log("Registered plugin %s with event handlers [%s]", plugin.getId(),
				String.join(", ", events.stream().map(Class::getSimpleName).sorted().toList()));
	}

	/**
	 * Register a tentative plugin that will only be fully registered if another
	 * plugin declares it as a dependency.
	 */
	public void registerTentativePlugin(Plugin plugin) {
		tentativePlugins.put(plugin.getId(), plugin);
		log.log("Registered tentative plugin %s", plugin.getId());
	}

	/**
	 * Check all registered plugins for dependencies on tentative plugins,
	 * and activate any tentative plugins that are needed.
	 */
	private void activateTentativePlugins() {
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Plugin plugin : new ArrayList<>(plugins.values())) {
				for (Plugin dependency : plugin.getDependencies()) {
					String depId = dependency.getId();
					if (tentativePlugins.containsKey(depId) && !plugins.containsKey(depId)) {
						log.log("Activating tentative plugin %s (needed by %s)", depId, plugin.getId());
						Plugin tentative = tentativePlugins.remove(depId);
						registerPlugin(tentative);
						changed = true;
					}
				}
			}
		}
	}

	public void registerCommand(Command command) {
		log.log("Registering command %s (variants: %d)", command.getName(), command.getVariants().size());
		commands.put(command.getName(), command);
	}

	/**
	 * Public read-only view of the registered commands. Used by the CLI for
	 * tab-completion and other tooling.
	 *
	 * @return unmodifiable map of registered commands
	 */
	public Map<String, Command> getCommands() {
		return java.util.Collections.unmodifiableMap(commands);
	}

	public void registerClient(Client client) throws InternalException {
		client.setAlive(true);
		client.setId(String.valueOf(getNewSessionId()));
		log.log("Registering client: %s", client);
		clients.add(client);
		client.sendOutput(CommandOutput.make(M_WELCOME).put(M_VERSION, Version.toMessage())
				.textf("Welcome to %s %s <%s>", Version.humanName, Version.versionNumber, Version.url));
		hooks.doEvent(OnStartClient.class, plugin -> plugin.onStartClient(client));
	}

	@SuppressWarnings("null") // Generic type T will never be null
	public <T extends GameSystem> T registerSystem(T system) {
		systems.put(system.getId(), system);
		Set<Class<? extends HookEvent>> events = hooks.registerHookHandler(system);
		log.log("Registered system %s with event handlers [%s]", system.getId(),
				String.join(", ", events.stream().map(Class::getSimpleName).sorted().toList()));
		return system;
	}

	private boolean anyClientAlive() {
		return clients.stream().anyMatch(Client::isAlive);
	}

	/**
	 * Get all registered clients.
	 * 
	 * @return Collection of all clients
	 */
	public Collection<Client> getClients() {
		return clients;
	}

	/**
	 * Process ticks for all tickable entities in the world.
	 * This is called after all client commands have been processed in the game
	 * loop.
	 * Ticks are based on world time advancement, not individual clients.
	 */
	private void processTicks() throws InternalException {
		com.benleskey.textengine.systems.TickSystem tickSystem = getSystem(
				com.benleskey.textengine.systems.TickSystem.class);
		tickSystem.processWorldTicks();
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

				// Process ticks after all client commands (only if clients still alive)
				if (anyClientAlive()) {
					processTicks();
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

		return CommandInput.make(CommandOutput.M_UNKNOWN_COMMAND).put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_LINE,
				line);
	}

	public void feedCommand(Client client, CommandInput input) throws InternalException {
		if (commands.containsKey(input.getId())) {
			commands.get(input.getId()).getFunction().run(client, input);
		} else {
			CommandInput unknown = CommandInput.make(CommandOutput.M_UNKNOWN_COMMAND)
					.put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_COMMAND, input);
			input.getLine().ifPresent(line -> unknown.put(CommandOutput.M_ORIGINAL_UNKNOWN_COMMAND_LINE, line));
			feedCommand(client, unknown);
		}
	}

	public Plugin getPlugin(Class<? extends Plugin> plugin) {
		Plugin p = plugins.get(plugin.getCanonicalName());
		if (p == null) {
			p = tentativePlugins.get(plugin.getCanonicalName());
		}
		return p;
	}
}
