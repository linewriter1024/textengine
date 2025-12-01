package com.benleskey.textengine.systems;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.actions.DropItemAction;
import com.benleskey.textengine.actions.MoveAction;
import com.benleskey.textengine.actions.TakeItemAction;
import com.benleskey.textengine.actions.WaitAction;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Acting;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.Action;
import com.benleskey.textengine.model.ActionValidation;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.util.Markup;

/**
 * ActorActionSystem manages actions for all actors (players and NPCs).
 * 
 * Actions extend Reference and are stored in the database with flexible
 * properties.
 * Action types are registered like entity types.
 * 
 * For Acting-tagged entities:
 * - System calls onActionReady() when no pending action
 * - Entity queues action via queueAction()
 * - System handles execution, broadcasts, time tracking
 * 
 * For players:
 * - Commands queue and execute actions instantly (auto-advance time)
 */
public class ActorActionSystem extends SingletonGameSystem implements OnSystemInitialize {

	// Action type registry
	private final Map<UniqueType, Class<? extends Action>> actionTypes = new HashMap<>();

	// Event type for all actions (reference points to action table)
	public UniqueType ACTION;

	// Action type constants
	public UniqueType ACTION_MOVE;
	public UniqueType ACTION_ITEM_TAKE;
	public UniqueType ACTION_ITEM_DROP;
	public UniqueType ACTION_WAIT;

	// Standard action property keys
	public UniqueType PROP_ACTOR;
	public UniqueType PROP_TARGET;
	public UniqueType PROP_TIME_REQUIRED;

	// Tag for Acting entities
	public UniqueType TAG_ACTING;
	public UniqueType TAG_LAST_ACTION_CHECK;

	private EventSystem eventSystem;
	private WorldSystem worldSystem;
	private EntitySystem entitySystem;
	private EntityTagSystem entityTagSystem;

	// Prepared statements
	private PreparedStatement insertActionStatement;
	private PreparedStatement getActionTypeStatement;
	private PreparedStatement insertPropertyStatement;
	private PreparedStatement getPropertyStatement;
	private PreparedStatement getPendingActionStatement;
	private PreparedStatement getActionCreationTimeStatement;

	public ActorActionSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		// No backwards compatibility - just recreate schema
		if (v == 0) {
			try (Statement s = game.db().createStatement()) {
				// Simple action table: just ID and type
				s.executeUpdate(
						"CREATE TABLE action (" +
								"action_id INTEGER PRIMARY KEY, " +
								"action_type INTEGER NOT NULL" +
								")");
				// Flexible property table for all action data
				s.executeUpdate(
						"CREATE TABLE action_property (" +
								"action_id INTEGER NOT NULL, " +
								"property_key INTEGER NOT NULL, " +
								"property_value INTEGER NOT NULL, " +
								"PRIMARY KEY (action_id, property_key)" +
								")");
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create action tables", e);
			}
			getSchema().setVersionNumber(1);
		}

		eventSystem = game.getSystem(EventSystem.class);
		worldSystem = game.getSystem(WorldSystem.class);
		entitySystem = game.getSystem(EntitySystem.class);
		entityTagSystem = game.getSystem(EntityTagSystem.class);

		UniqueTypeSystem uts = game.getSystem(UniqueTypeSystem.class);

		// Define event types
		ACTION = uts.getType("action");

		// Define action types
		ACTION_MOVE = uts.getType("action_move");
		ACTION_ITEM_TAKE = uts.getType("action_item_take");
		ACTION_ITEM_DROP = uts.getType("action_item_drop");
		ACTION_WAIT = uts.getType("action_wait");

		// Define property keys
		PROP_ACTOR = uts.getType("action_prop_actor");
		PROP_TARGET = uts.getType("action_prop_target");
		PROP_TIME_REQUIRED = uts.getType("action_prop_time_required");

		// Define tags
		TAG_ACTING = uts.getType("entity_tag_acting");
		TAG_LAST_ACTION_CHECK = uts.getType("entity_tag_last_action_check");

		// Prepare SQL statements
		try {
			insertActionStatement = game.db().prepareStatement(
					"INSERT INTO action (action_id, action_type) VALUES (?, ?)");

			getActionTypeStatement = game.db().prepareStatement(
					"SELECT action_type FROM action WHERE action_id = ?");

			insertPropertyStatement = game.db().prepareStatement(
					"INSERT OR REPLACE INTO action_property (action_id, property_key, property_value) VALUES (?, ?, ?)");

			getPropertyStatement = game.db().prepareStatement(
					"SELECT property_value FROM action_property WHERE action_id = ? AND property_key = ?");

			// Get pending action for an actor (using PROP_ACTOR property)
			getPendingActionStatement = game.db().prepareStatement(
					"SELECT action.action_id " +
							"FROM action " +
							"JOIN action_property AS actor_prop ON action.action_id = actor_prop.action_id " +
							"    AND actor_prop.property_key = ? " +
							"JOIN event ON event.reference = action.action_id " +
							"WHERE actor_prop.property_value = ? " +
							"AND event.time <= ? " +
							"AND action.action_id IN " + eventSystem.getValidEventsSubquery("action.action_id") + " " +
							"ORDER BY event.time ASC LIMIT 1");

			getActionCreationTimeStatement = game.db().prepareStatement(
					"SELECT event.time " +
							"FROM event " +
							"WHERE event.reference = ? " +
							"AND event.type = ? " +
							"AND event.event_id NOT IN (SELECT event_cancel.reference FROM event AS event_cancel WHERE event_cancel.type = ? AND event_cancel.time <= ?) "
							+
							"ORDER BY event.event_order DESC LIMIT 1");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare action statements", e);
		}

		// Register action classes
		registerActionType(ACTION_MOVE, MoveAction.class);
		registerActionType(ACTION_ITEM_TAKE, TakeItemAction.class);
		registerActionType(ACTION_ITEM_DROP, DropItemAction.class);
		registerActionType(ACTION_WAIT, WaitAction.class);
	}

	/**
	 * Register an action type with its implementation class.
	 */
	public void registerActionType(UniqueType actionType, Class<? extends Action> actionClass) {
		actionTypes.put(actionType, actionClass);
		log.log("Registered action type %s to class %s", actionType, actionClass.getCanonicalName());
	}

	/**
	 * Get the action class for a type.
	 */
	public Class<? extends Action> getActionClass(UniqueType type) {
		return actionTypes.get(type);
	}

	/**
	 * Get an action by its ID with a specific class.
	 */
	public synchronized <T extends Action> T get(long id, Class<T> clazz) {
		try {
			return clazz.getDeclaredConstructor(long.class, Game.class).newInstance(id, game);
		} catch (Exception e) {
			throw new InternalException("Unable to create action of class " + clazz.toGenericString(), e);
		}
	}

	/**
	 * Get an action by its ID, looking up the type.
	 */
	public synchronized Action get(long id) throws DatabaseException {
		try {
			getActionTypeStatement.setLong(1, id);
			try (ResultSet rs = getActionTypeStatement.executeQuery()) {
				if (rs.next()) {
					UniqueType actionType = new UniqueType(rs.getLong("action_type"),
							game.getSystem(UniqueTypeSystem.class));
					Class<? extends Action> actionClass = getActionClass(actionType);
					if (actionClass == null) {
						throw new InternalException("Unknown action type: " + actionType);
					}
					return get(id, actionClass);
				}
			}
			throw new InternalException("Action not found: " + id);
		} catch (SQLException e) {
			throw new DatabaseException("Could not get action " + id, e);
		}
	}

	/**
	 * Create a new action in the database.
	 */
	public synchronized <T extends Action> T add(Class<T> clazz, Actor actor, Entity target, DTime timeRequired)
			throws DatabaseException {
		try {
			T dummy = get(0, clazz);
			UniqueType actionType = dummy.getActionType();

			long actionId = game.getNewGlobalId();

			insertActionStatement.setLong(1, actionId);
			insertActionStatement.setLong(2, actionType.type());
			insertActionStatement.executeUpdate();

			T action = get(actionId, clazz);

			// Set standard properties
			action.setActor(actor);
			action.setTarget(target);
			action.setTimeRequired(timeRequired);

			return action;
		} catch (SQLException e) {
			throw new DatabaseException("Unable to create action", e);
		}
	}

	/**
	 * Get a property value for an action.
	 * 
	 * @return Optional containing the value, or empty if not set
	 */
	public synchronized Optional<Long> getActionProperty(Action action, UniqueType key) {
		try {
			getPropertyStatement.setLong(1, action.getId());
			getPropertyStatement.setLong(2, key.type());
			try (ResultSet rs = getPropertyStatement.executeQuery()) {
				if (rs.next()) {
					return Optional.of(rs.getLong("property_value"));
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Unable to get action property", e);
		}
		return Optional.empty();
	}

	/**
	 * Set a property value for an action.
	 */
	public synchronized void setActionProperty(Action action, UniqueType key, long value) {
		try {
			insertPropertyStatement.setLong(1, action.getId());
			insertPropertyStatement.setLong(2, key.type());
			insertPropertyStatement.setLong(3, value);
			insertPropertyStatement.executeUpdate();
		} catch (SQLException e) {
			throw new DatabaseException("Unable to set action property", e);
		}
	}

	/**
	 * Queue an action for an actor.
	 * Validates the action first. If invalid, returns the validation result with
	 * error output.
	 * Players: auto-advance time and execute immediately.
	 * NPCs: store in database for later execution.
	 * 
	 * @return ActionValidation indicating success or failure with error output for
	 *         players
	 */
	public ActionValidation queueAction(Actor actor, UniqueType actionType, Entity target, DTime timeRequired)
			throws DatabaseException {
		// Create the action in the database
		Class<? extends Action> actionClass = getActionClass(actionType);
		if (actionClass == null) {
			return ActionValidation.failure(
					CommandOutput.make("action")
							.error("invalid_action_type")
							.text(Markup.escape("That action doesn't exist.")));
		}

		Action action = add(actionClass, actor, target, timeRequired);

		// Validate the action
		ActionValidation validation = action.canExecute();
		if (!validation.isValid()) {
			return validation;
		}

		DTime currentTime = worldSystem.getCurrentTime();
		boolean isPlayer = entityTagSystem.hasTag(actor, entitySystem.TAG_AVATAR, currentTime);

		// Player actions advance time so that they will execute immediately.
		if (isPlayer) {
			worldSystem.incrementCurrentTime(timeRequired);
		}

		// Create ACTION event with reference to action
		eventSystem.addEvent(ACTION, currentTime, action);

		return ActionValidation.success();
	}

	/**
	 * Execute an action directly (without queueing).
	 * Used internally for NPC action execution.
	 */
	public boolean executeAction(Actor actor, UniqueType actionType, Entity target, DTime timeRequired) {
		Class<? extends Action> actionClass = getActionClass(actionType);
		if (actionClass == null) {
			return false;
		}

		Action action = add(actionClass, actor, target, timeRequired);

		// Check if action can be executed
		ActionValidation validation = action.canExecute();
		if (!validation.isValid()) {
			return false;
		}

		CommandOutput result = action.execute();
		return result != null;
	}

	/**
	 * Get pending action for an actor.
	 */
	public synchronized Action getPendingAction(Actor actor) throws DatabaseException {
		DTime currentTime = worldSystem.getCurrentTime();

		try {
			getPendingActionStatement.setLong(1, PROP_ACTOR.type());
			getPendingActionStatement.setLong(2, actor.getId());
			getPendingActionStatement.setLong(3, currentTime.raw());
			eventSystem.setValidEventsSubqueryParameters(getPendingActionStatement, 4, ACTION, currentTime);

			try (ResultSet rs = getPendingActionStatement.executeQuery()) {
				if (rs.next()) {
					long actionId = rs.getLong("action_id");
					return get(actionId);
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Unable to query pending action", e);
		}

		return null;
	}

	/**
	 * Get the time when an action was created (from its event).
	 */
	public synchronized DTime getActionCreationTime(Action action) throws DatabaseException {
		DTime currentTime = worldSystem.getCurrentTime();
		try {
			getActionCreationTimeStatement.setLong(1, action.getId());
			getActionCreationTimeStatement.setLong(2, ACTION.type());
			getActionCreationTimeStatement.setLong(3, eventSystem.etCancel.type());
			getActionCreationTimeStatement.setLong(4, currentTime.raw());

			try (ResultSet rs = getActionCreationTimeStatement.executeQuery()) {
				if (rs.next()) {
					return DTime.fromMilliseconds(rs.getLong("time"));
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Unable to get action creation time", e);
		}
		return null;
	}

	/**
	 * Get the time when an action will be ready to execute.
	 */
	public synchronized long getActionReadyTime(Action action) throws DatabaseException {
		DTime creationTime = getActionCreationTime(action);
		if (creationTime == null) {
			return Long.MAX_VALUE;
		}
		DTime timeRequired = action.getTimeRequired();
		return creationTime.toMilliseconds() + timeRequired.toMilliseconds();
	}

	/**
	 * Check if an action is ready to execute.
	 */
	public boolean isActionReady(Action action, DTime currentTime) throws DatabaseException {
		long readyTime = getActionReadyTime(action);
		return readyTime != Long.MAX_VALUE && currentTime.toMilliseconds() >= readyTime;
	}

	/**
	 * Execute and clear a pending action.
	 */
	public boolean executePendingAction(Actor actor, Action action, DTime currentTime) throws DatabaseException {
		CommandOutput result = action.execute();
		eventSystem.cancelEventsByTypeAndReference(ACTION, action, currentTime);

		return result != null;
	}

	/**
	 * Get description of pending action for observers.
	 */
	public String getPendingActionDescription(Actor actor) throws DatabaseException {
		Action action = getPendingAction(actor);
		if (action == null) {
			return null;
		}
		return action.getDescription();
	}

	/**
	 * Process a single tick for an Acting entity.
	 * Uses worldSystem.getCurrentTime() for all time references.
	 * Returns true if processed successfully.
	 */
	public boolean processActingEntitySingleTick(Acting acting, Actor actor, DTime interval) throws DatabaseException {
		DTime currentTime = worldSystem.getCurrentTime();

		// Check if entity has pending action
		Action pendingAction = getPendingAction(actor);

		if (pendingAction != null) {
			// Has pending action - check if ready to execute
			if (isActionReady(pendingAction, currentTime)) {
				executePendingAction(actor, pendingAction, currentTime);
				// Action completed - continue to potentially queue new action
			} else {
				// Action not ready yet, wait
				return true;
			}
		}

		// No pending action (or action just completed) - call entity to decide
		acting.onActionReady();

		return true;
	}
}
