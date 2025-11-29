package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.actions.*;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Acting;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.Reference;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.util.Markup;

import java.lang.reflect.Constructor;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ActorActionSystem manages actions for all actors (players and NPCs).
 * 
 * Actions are References (like entities) stored in the database.
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

	// Tag for Acting entities
	public UniqueType TAG_ACTING;
	public UniqueType TAG_LAST_ACTION_CHECK;

	private EventSystem eventSystem;
	private WorldSystem worldSystem;
	private EntitySystem entitySystem;
	private EntityTagSystem entityTagSystem;

	// Prepared statements
	private PreparedStatement insertActionStatement;
	private PreparedStatement loadActionStatement;
	private PreparedStatement getPendingActionStatement;
	private PreparedStatement getActionReadyTimeStatement;
	private PreparedStatement getActingEntitiesStatement;

	public ActorActionSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();
		if (v == 0) {
			try (Statement s = game.db().createStatement()) {
				s.executeUpdate(
						"CREATE TABLE action (" +
								"action_id INTEGER PRIMARY KEY, " +
								"actor_id INTEGER NOT NULL, " +
								"action_type INTEGER NOT NULL, " +
								"target_entity_id INTEGER NOT NULL, " +
								"time_required_ms INTEGER NOT NULL" +
								")");
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create action table", e);
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

		// Define tags
		TAG_ACTING = uts.getType("entity_tag_acting");
		TAG_LAST_ACTION_CHECK = uts.getType("entity_tag_last_action_check");

		// Prepare SQL statements
		try {
			insertActionStatement = game.db().prepareStatement(
					"INSERT INTO action (action_id, actor_id, action_type, target_entity_id, time_required_ms) VALUES (?, ?, ?, ?, ?)");

			loadActionStatement = game.db().prepareStatement(
					"SELECT actor_id, action_type, target_entity_id, time_required_ms, event.time " +
							"FROM action " +
							"JOIN event ON event.reference = action.action_id " +
							"WHERE action.action_id = ? " +
							"AND event.reference IN " + eventSystem.getValidEventsSubquery("action.action_id"));

			getPendingActionStatement = game.db().prepareStatement(
					"SELECT action.action_id, event.time, action.time_required_ms " +
							"FROM action " +
							"JOIN event ON event.reference = action.action_id " +
							"WHERE action.actor_id = ? " +
							"AND event.time <= ? " +
							"AND action.action_id IN " + eventSystem.getValidEventsSubquery("action.action_id") + " " +
							"ORDER BY event.time ASC LIMIT 1");
			getActionReadyTimeStatement = game.db().prepareStatement(
					"SELECT event.time, time_required_ms " +
							"FROM action " +
							"JOIN event ON event.reference = action.action_id " +
							"WHERE action.action_id = ? " +
							"AND action.action_id IN " + eventSystem.getValidEventsSubquery("action.action_id"));
			getActingEntitiesStatement = game.db().prepareStatement(
					"SELECT DISTINCT entity_id FROM entity_tag " +
							"JOIN event ON event.reference = entity_tag.entity_tag_id " +
							"WHERE entity_tag.entity_tag_type = ? " +
							"AND event.reference IN " + eventSystem.getValidEventsSubquery("entity_tag.entity_tag_id"));
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
	 * Create an action instance from a Reference.
	 */
	private synchronized Action createActionFromReference(Reference actionRef, DTime currentTime)
			throws DatabaseException {
		try {
			loadActionStatement.setLong(1, actionRef.getId());
			eventSystem.setValidEventsSubqueryParameters(loadActionStatement, 2, ACTION, currentTime);

			try (ResultSet rs = loadActionStatement.executeQuery()) {
				if (rs.next()) {
					long actorId = rs.getLong("actor_id");
					long actionTypeId = rs.getLong("action_type");
					long targetId = rs.getLong("target_entity_id");
					long timeRequiredMs = rs.getLong("time_required_ms");

					Actor actor = (Actor) entitySystem.get(actorId);
					Entity target = entitySystem.get(targetId);
					UniqueType actionType = new UniqueType(actionTypeId, game.getSystem(UniqueTypeSystem.class));
					DTime timeRequired = DTime.fromMilliseconds(timeRequiredMs);

					return createAction(actionType, actor, target, timeRequired);
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Unable to load action from reference", e);
		}
		return null;
	}

	/**
	 * Create an action instance from parameters.
	 */
	private Action createAction(UniqueType actionType, Actor actor, Entity target, DTime timeRequired) {
		Class<? extends Action> actionClass = actionTypes.get(actionType);
		if (actionClass == null) {
			throw new InternalException(String.format("Cannot create action %s: not registered", actionType));
		}

		try {
			Constructor<? extends Action> constructor = actionClass.getDeclaredConstructor(
					Game.class, Actor.class, Entity.class, DTime.class);
			return constructor.newInstance(game, actor, target, timeRequired);
		} catch (Exception e) {
			throw new InternalException(String.format("Failed to create action of type %s", actionType), e);
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
		// Validate the action first
		Action action = createAction(actionType, actor, target, timeRequired);
		if (action == null) {
			return ActionValidation.failure(
					CommandOutput.make("action")
							.error("invalid_action_type")
							.text(Markup.escape("That action doesn't exist.")));
		}

		ActionValidation validation = action.canExecute();
		if (!validation.isValid()) {
			return validation;
		}

		DTime currentTime = worldSystem.getCurrentTime();
		boolean isPlayer = entityTagSystem.hasTag(actor, entitySystem.TAG_AVATAR, currentTime);

		if (isPlayer) {
			// Players execute immediately with time auto-advance
			worldSystem.incrementCurrentTime(timeRequired);
			CommandOutput result = action.execute();
			if (result == null) {
				return ActionValidation.failure(
						CommandOutput.make("action")
								.error("execution_failed")
								.text(Markup.escape("Something went wrong.")));
			}
			// Result was already broadcast by the action, player will receive via broadcast
		} else {
			// NPCs queue action in database for later execution
			long actionId = game.getNewGlobalId();

			synchronized (this) {
				try {
					insertActionStatement.setLong(1, actionId);
					insertActionStatement.setLong(2, actor.getId());
					insertActionStatement.setLong(3, actionType.type());
					insertActionStatement.setLong(4, target.getId());
					insertActionStatement.setLong(5, timeRequired.toMilliseconds());
					insertActionStatement.executeUpdate();
				} catch (SQLException e) {
					throw new DatabaseException("Unable to create action", e);
				}
			}

			// Create generic ACTION event with reference to action table entry
			eventSystem.addEvent(ACTION, currentTime, new Reference(actionId, game));
		}

		return ActionValidation.success();
	}

	/**
	 * Execute an action.
	 * Used internally for NPC action execution.
	 */
	public boolean executeAction(Actor actor, UniqueType actionType, Entity target, DTime timeRequired) {
		Action action = createAction(actionType, actor, target, timeRequired);
		if (action == null) {
			return false;
		}

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
	public synchronized Reference getPendingAction(Actor actor) throws DatabaseException {
		DTime currentTime = worldSystem.getCurrentTime();

		try {
			getPendingActionStatement.setLong(1, actor.getId());
			getPendingActionStatement.setLong(2, currentTime.raw());
			eventSystem.setValidEventsSubqueryParameters(getPendingActionStatement, 3, ACTION, currentTime);

			try (ResultSet rs = getPendingActionStatement.executeQuery()) {
				if (rs.next()) {
					long actionId = rs.getLong("action_id");
					return new Reference(actionId, game);
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Unable to query pending action", e);
		}

		return null;
	}

	/**
	 * Get the time when an action will be ready to execute.
	 */
	public synchronized long getActionReadyTime(Reference actionRef) throws DatabaseException {
		DTime currentTime = worldSystem.getCurrentTime();
		try {
			getActionReadyTimeStatement.setLong(1, actionRef.getId());
			eventSystem.setValidEventsSubqueryParameters(getActionReadyTimeStatement, 2, ACTION, currentTime);

			try (ResultSet rs = getActionReadyTimeStatement.executeQuery()) {
				if (rs.next()) {
					long createdAtMs = rs.getLong("time");
					long timeRequiredMs = rs.getLong("time_required_ms");
					return createdAtMs + timeRequiredMs;
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Unable to get action ready time", e);
		}

		return Long.MAX_VALUE; // Action not found
	}

	/**
	 * Check if an action is ready to execute.
	 */
	public boolean isActionReady(Reference actionRef, DTime currentTime) throws DatabaseException {
		long readyTime = getActionReadyTime(actionRef);
		return readyTime != Long.MAX_VALUE && currentTime.toMilliseconds() >= readyTime;
	}

	/**
	 * Execute and clear a pending action.
	 */
	public boolean executePendingAction(Actor actor, Reference actionRef, DTime currentTime) throws DatabaseException {
		Action action = createActionFromReference(actionRef, currentTime);
		if (action == null) {
			return false;
		}

		CommandOutput result = action.execute();
		eventSystem.cancelEvent(actionRef);

		return result != null;
	}

	/**
	 * Get description of pending action for observers.
	 */
	public String getPendingActionDescription(Actor actor) throws DatabaseException {
		Reference actionRef = getPendingAction(actor);
		if (actionRef == null) {
			return null;
		}

		Action action = createActionFromReference(actionRef, worldSystem.getCurrentTime());
		return action != null ? action.getDescription() : null;
	}

	/**
	 * Tick Acting entities - check if ready for new action.
	 * Called by TickSystem.
	 * 
	 * @deprecated Use TickSystem's unified time-ordered processing instead
	 */
	@Deprecated
	public void tickActingEntities(DTime currentTime, DTime timeSinceLastTick) throws DatabaseException {
		// Get all Acting entities
		List<Entity> actingEntities = new ArrayList<>();

		synchronized (this) {
			try {
				getActingEntitiesStatement.setLong(1, TAG_ACTING.type());
				eventSystem.setValidEventsSubqueryParameters(getActingEntitiesStatement, 2, entityTagSystem.etEntityTag,
						currentTime);

				try (ResultSet rs = getActingEntitiesStatement.executeQuery()) {
					while (rs.next()) {
						Entity entity = entitySystem.get(rs.getLong("entity_id"));
						if (entity instanceof Acting) {
							actingEntities.add(entity);
						}
					}
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to query acting entities", e);
			}
		}

		// Process each Acting entity
		for (Entity entity : actingEntities) {
			processActingEntity((Acting) entity, (Actor) entity, currentTime);
		}
	}

	/**
	 * Process a single Acting entity.
	 */
	private void processActingEntity(Acting acting, Actor actor, DTime currentTime) throws DatabaseException {
		// Check if entity has pending action
		Reference pendingAction = getPendingAction(actor);

		if (pendingAction != null) {
			// Has pending action - check if ready to execute
			if (isActionReady(pendingAction, currentTime)) {
				executePendingAction(actor, pendingAction, currentTime);
			}
		} else {
			// No pending action - check if time to decide on new action
			Long lastCheckMs = entityTagSystem.getTagValue(actor, TAG_LAST_ACTION_CHECK, currentTime);
			DTime lastCheck;

			if (lastCheckMs == null) {
				// Never checked before - use entity creation time
				Long creationMs = entityTagSystem.getTagValue(actor, entitySystem.TAG_ENTITY_CREATED, currentTime);
				if (creationMs != null) {
					lastCheck = DTime.fromMilliseconds(creationMs);
				} else {
					// Fallback: entity created before creation time tracking
					lastCheck = DTime.fromMilliseconds(0);
				}
			} else {
				lastCheck = DTime.fromMilliseconds(lastCheckMs);
			}

			DTime interval = acting.getActionInterval();
			DTime timeSinceLastCheck = DTime
					.fromMilliseconds(currentTime.toMilliseconds() - lastCheck.toMilliseconds());

			// Check if enough time has passed for action decision(s)
			if (timeSinceLastCheck.toMilliseconds() >= interval.toMilliseconds()) {
				// Calculate how many action decisions should have occurred
				long decisionCount = timeSinceLastCheck.toMilliseconds() / interval.toMilliseconds();

				// Trigger action decision(s) with correct time for each check
				for (int i = 0; i < decisionCount; i++) {
					// Call entity to decide what action to take
					acting.onActionReady();

					// If entity queued an action, stop - don't make multiple decisions
					Reference newAction = getPendingAction(actor);
					if (newAction != null) {
						break;
					}
				}

				// Update last check time to account for all processed intervals
				DTime newLastCheck = DTime
						.fromMilliseconds(lastCheck.toMilliseconds() + (decisionCount * interval.toMilliseconds()));
				entityTagSystem.updateTagValue(actor, TAG_LAST_ACTION_CHECK, newLastCheck.toMilliseconds(),
						currentTime);
			}
		}
	}

	/**
	 * Process a single tick for an Acting entity.
	 * Uses worldSystem.getCurrentTime() for all time references.
	 * Returns true if processed successfully.
	 */
	public boolean processActingEntitySingleTick(Acting acting, Actor actor, DTime interval) throws DatabaseException {
		DTime currentTime = worldSystem.getCurrentTime();

		// Check if entity has pending action
		Reference pendingAction = getPendingAction(actor);

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
