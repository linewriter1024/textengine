package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.entities.Acting;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.actions.*;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.Reference;
import com.benleskey.textengine.model.UniqueType;

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
	
	// Action type constants
	public UniqueType ACTION_MOVE;
	public UniqueType ACTION_ITEM_TAKE;
	public UniqueType ACTION_ITEM_DROP;
	
	// Tag for Acting entities
	public UniqueType TAG_ACTING;
	public UniqueType TAG_LAST_ACTION_CHECK;
	
	// Event type for entity tags
	private UniqueType etEntityTag;
	
	private EventSystem eventSystem;
	private WorldSystem worldSystem;
	private EntitySystem entitySystem;
	private EntityTagSystem entityTagSystem;
	
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
")"
);
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
		etEntityTag = uts.getType("entity_tag");
		
		// Define action types
		ACTION_MOVE = uts.getType("action_move");
		ACTION_ITEM_TAKE = uts.getType("action_item_take");
		ACTION_ITEM_DROP = uts.getType("action_item_drop");
		
		// Define tags
		TAG_ACTING = uts.getType("entity_tag_acting");
		TAG_LAST_ACTION_CHECK = uts.getType("entity_tag_last_action_check");
		
		// Register action classes
		registerActionType(ACTION_MOVE, MoveAction.class);
		registerActionType(ACTION_ITEM_TAKE, TakeItemAction.class);
		registerActionType(ACTION_ITEM_DROP, DropItemAction.class);
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
	private Action createActionFromReference(Reference actionRef, DTime currentTime) throws DatabaseException {
		try (PreparedStatement ps = game.db().prepareStatement(
"SELECT actor_id, action_type, target_entity_id, time_required_ms, event.time " +
"FROM action " +
"JOIN event ON event.reference = action.action_id " +
"WHERE action.action_id = ? " +
"AND event.reference IN " + eventSystem.getValidEventsSubquery("action.action_id"))) {
			
			ps.setLong(1, actionRef.getId());
			eventSystem.setValidEventsSubqueryParameters(ps, 2, ACTION_MOVE, currentTime);
			
			try (ResultSet rs = ps.executeQuery()) {
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
			log.log("Unknown action type: %s", actionType);
			return null;
		}
		
		try {
			Constructor<? extends Action> constructor = actionClass.getDeclaredConstructor(
Game.class, Actor.class, Entity.class, DTime.class);
			return constructor.newInstance(game, actor, target, timeRequired);
		} catch (Exception e) {
			log.log("Failed to create action of type %s: %s", actionType, e.getMessage());
			return null;
		}
	}
	
	/**
	 * Queue an action for an actor.
	 * Players: auto-advance time and execute immediately.
	 * NPCs: store in database for later execution.
	 */
	public void queueAction(Actor actor, UniqueType actionType, Entity target, DTime timeRequired) throws DatabaseException {
		DTime currentTime = worldSystem.getCurrentTime();
		boolean isPlayer = entityTagSystem.hasTag(actor, entitySystem.TAG_AVATAR, currentTime);
		
		if (isPlayer) {
			// Players execute immediately with time auto-advance
			worldSystem.incrementCurrentTime(timeRequired);
			log.log("Player %d action %s completed instantly (advanced time by %d ms)", 
				actor.getId(), actionType, timeRequired.toMilliseconds());
		} else {
			// NPCs queue action in database
			long actionId = game.getNewGlobalId();
			
			try (PreparedStatement ps = game.db().prepareStatement(
"INSERT INTO action (action_id, actor_id, action_type, target_entity_id, time_required_ms) VALUES (?, ?, ?, ?, ?)")) {
				ps.setLong(1, actionId);
				ps.setLong(2, actor.getId());
				ps.setLong(3, actionType.type());
				ps.setLong(4, target.getId());
				ps.setLong(5, timeRequired.toMilliseconds());
				ps.execute();
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create action", e);
			}
			
			eventSystem.addEvent(actionType, currentTime, new Reference(actionId, game));
			
			log.log("NPC %d queued action %s (id=%d, requires %d ms) at time %d, will be ready at %d", 
				actor.getId(), actionType, actionId, timeRequired.toMilliseconds(), 
				currentTime.toMilliseconds(), currentTime.toMilliseconds() + timeRequired.toMilliseconds());
		}
	}
	
	/**
	 * Validate whether an action can be executed without actually queueing it.
	 * Returns the created action if valid, null if invalid.
	 * Useful for NPCs to pre-check actions before queueing.
	 */
	public ActionValidation validateAction(Actor actor, UniqueType actionType, Entity target, DTime timeRequired) {
		Action action = createAction(actionType, actor, target, timeRequired);
		if (action == null) {
			return ActionValidation.failure("invalid_action_type", "Unknown action type");
		}
		return action.canExecute();
	}
	
	/**
	 * Execute an action.
	 */
	public boolean executeAction(Actor actor, UniqueType actionType, Entity target, DTime timeRequired) {
		Action action = createAction(actionType, actor, target, timeRequired);
		if (action == null) {
			return false;
		}
		
		// Check if action can be executed
		ActionValidation validation = action.canExecute();
		if (!validation.isValid()) {
			log.log("Actor %d cannot execute %s action: %s", actor.getId(), actionType, validation.getErrorMessage());
			return false;
		}
		
		boolean success = action.execute();
		log.log("Actor %d executed %s action: %s", actor.getId(), actionType, success ? "success" : "failed");
		return success;
	}
	
	/**
	 * Get pending action for an actor.
	 */
	public Reference getPendingAction(Actor actor) throws DatabaseException {
		DTime currentTime = worldSystem.getCurrentTime();
		
		try (PreparedStatement ps = game.db().prepareStatement(
"SELECT action_id, event.time, time_required_ms " +
"FROM action " +
"JOIN event ON event.reference = action.action_id " +
"WHERE action.actor_id = ? " +
"AND event.reference IN " + eventSystem.getValidEventsSubquery("action.action_id") + 
" ORDER BY event.time ASC " +
"LIMIT 1")) {
			
			ps.setLong(1, actor.getId());
			eventSystem.setValidEventsSubqueryParameters(ps, 2, ACTION_MOVE, currentTime);
			
			try (ResultSet rs = ps.executeQuery()) {
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
	public long getActionReadyTime(Reference actionRef) throws DatabaseException {
		try (PreparedStatement ps = game.db().prepareStatement(
			"SELECT event.time, time_required_ms " +
			"FROM action " +
			"JOIN event ON event.reference = action.action_id " +
			"WHERE action.action_id = ?")) {
			
			ps.setLong(1, actionRef.getId());
			
			try (ResultSet rs = ps.executeQuery()) {
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
	}	/**
	 * Execute and clear a pending action.
	 */
	public boolean executePendingAction(Actor actor, Reference actionRef, DTime currentTime) throws DatabaseException {
		Action action = createActionFromReference(actionRef, currentTime);
		if (action == null) {
			return false;
		}
		
		boolean success = action.execute();
		eventSystem.cancelEvent(actionRef);
		
		log.log("Actor %d executed pending action %d: %s", actor.getId(), actionRef.getId(), 
			success ? "success" : "failed");
		
		return success;
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
	 * Process a single tick for an Acting entity (called by TickSystem for fair time-ordered processing).
	 * This processes one decision/action cycle:
	 * 1. If has pending action and it's ready, execute it
	 * 2. If no pending action (or just executed one), make a new decision
	 */
	public void processActingEntitySingleTick(Actor actor, Acting acting, DTime tickTime, DTime currentTime) throws DatabaseException {
		// Check if entity has pending action
		Reference pendingAction = getPendingAction(actor);
		
		if (pendingAction != null) {
			// Has pending action - check if ready to execute
			if (isActionReady(pendingAction, tickTime)) {
				log.log("Acting entity %d: action ready at %d, executing", actor.getId(), tickTime.toMilliseconds());
				executePendingAction(actor, pendingAction, currentTime);
				// After execution, fall through to make new decision
			} else {
				// Action not ready yet - skip this tick
				log.log("Acting entity %d: waiting for action to be ready", actor.getId());
				return;
			}
		}
		
		// No pending action (or just finished one) - make decision
		log.log("Acting entity %d: ready for new action", actor.getId());
		acting.onActionReady();
	}
	
	/**
	 * Tick Acting entities - check if ready for new action.
	 * Called by TickSystem.
	 * @deprecated Use TickSystem's unified time-ordered processing instead
	 */
	@Deprecated
	public void tickActingEntities(DTime currentTime, DTime timeSinceLastTick) throws DatabaseException {
		// Get all Acting entities
		List<Entity> actingEntities = new ArrayList<>();
		
		try (PreparedStatement ps = game.db().prepareStatement(
			"SELECT DISTINCT entity_id FROM entity_tag " +
			"JOIN event ON event.reference = entity_tag.entity_tag_id " +
			"WHERE entity_tag.entity_tag_type = ? " +
			"AND event.reference IN " + eventSystem.getValidEventsSubquery("entity_tag.entity_tag_id"))) {
			
			ps.setLong(1, TAG_ACTING.type());
			eventSystem.setValidEventsSubqueryParameters(ps, 2, etEntityTag, currentTime);
			
			try (ResultSet rs = ps.executeQuery()) {
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
		
		// Process each Acting entity
		for (Entity entity : actingEntities) {
			processActingEntity((Acting) entity, (Actor) entity, currentTime);
		}
	}	/**
	 * Process a single Acting entity.
	 */
	private void processActingEntity(Acting acting, Actor actor, DTime currentTime) throws DatabaseException {
		// Check if entity has pending action
		Reference pendingAction = getPendingAction(actor);
		
		if (pendingAction != null) {
			// Has pending action - check if ready to execute
			if (isActionReady(pendingAction, currentTime)) {
				log.log("Acting entity %d: action ready, executing", actor.getId());
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
			DTime timeSinceLastCheck = DTime.fromMilliseconds(currentTime.toMilliseconds() - lastCheck.toMilliseconds());
			
			// Check if enough time has passed for action decision(s)
			if (timeSinceLastCheck.toMilliseconds() >= interval.toMilliseconds()) {
				// Calculate how many action decisions should have occurred
				long decisionCount = timeSinceLastCheck.toMilliseconds() / interval.toMilliseconds();
				
				// Trigger action decision(s) with correct time for each check
			for (int i = 0; i < decisionCount; i++) {
				log.log("Acting entity %d: ready for new action (decision %d/%d)", 
					actor.getId(), i + 1, decisionCount);					// Call entity to decide what action to take
					acting.onActionReady();
					
					// If entity queued an action, stop - don't make multiple decisions
					Reference newAction = getPendingAction(actor);
					if (newAction != null) {
						log.log("Acting entity %d: action queued, stopping decision loop", actor.getId());
						break;
					}
				}
				
				// Update last check time to account for all processed intervals
				DTime newLastCheck = DTime.fromMilliseconds(lastCheck.toMilliseconds() + (decisionCount * interval.toMilliseconds()));
				entityTagSystem.updateTagValue(actor, TAG_LAST_ACTION_CHECK, newLastCheck.toMilliseconds(), currentTime);
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
		log.log("processActingEntitySingleTick: actor=%d, currentTime=%d, interval=%d", 
			actor.getId(), currentTime.toMilliseconds(), interval.toMilliseconds());
		
		// Check if entity has pending action
		Reference pendingAction = getPendingAction(actor);
		
		if (pendingAction != null) {
			log.log("Actor %d has pending action %d", actor.getId(), pendingAction.getId());
			// Has pending action - check if ready to execute
			if (isActionReady(pendingAction, currentTime)) {
				log.log("Actor %d pending action %d is ready, executing", actor.getId(), pendingAction.getId());
				executePendingAction(actor, pendingAction, currentTime);
				// Action completed - continue to potentially queue new action
			} else {
				log.log("Actor %d pending action %d not ready yet, waiting", actor.getId(), pendingAction.getId());
				// Action not ready yet, wait
				return true;
			}
		}
		
		// No pending action (or action just completed) - call entity to decide
		log.log("Actor %d calling onActionReady", actor.getId());
		acting.onActionReady();
		
		return true;
	}
}
