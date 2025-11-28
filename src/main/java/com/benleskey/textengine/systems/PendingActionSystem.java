package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.Reference;
import com.benleskey.textengine.model.UniqueType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Generic system for managing pending actions that take time to complete.
 * Stores actions as events - created when queued, canceled when completed.
 * 
 * Players: Auto-increment world time to complete actions instantly
 * NPCs: Accumulate time over ticks until action completes
 */
public class PendingActionSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	public UniqueType ACTION_MOVE;
	public UniqueType ACTION_ITEM_TAKE;
	public UniqueType ACTION_ITEM_DROP;
	
	private EventSystem eventSystem;
	private WorldSystem worldSystem;
	private EntitySystem entitySystem;
	private EntityTagSystem entityTagSystem;
	private UniqueTypeSystem uniqueTypeSystem;
	
	public static class PendingAction {
		public final long actionId;
		public final UniqueType type;
		public final DTime timeRequired;
		public final long targetEntityId;
		public final DTime createdAt;
		
		public PendingAction(long actionId, UniqueType type, DTime timeRequired, long targetEntityId, DTime createdAt) {
			this.actionId = actionId;
			this.type = type;
			this.timeRequired = timeRequired;
			this.targetEntityId = targetEntityId;
			this.createdAt = createdAt;
		}
		
		public boolean isReady(DTime currentTime) {
			return currentTime.toMilliseconds() - createdAt.toMilliseconds() >= timeRequired.toMilliseconds();
		}
	}
	
	public PendingActionSystem(Game game) {
		super(game);
	}
	
	@Override
	public void onSystemInitialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();
		if (v == 0) {
			try (Statement s = game.db().createStatement()) {
				s.executeUpdate(
"CREATE TABLE pending_action (" +
"action_id INTEGER PRIMARY KEY, " +
"actor_id INTEGER NOT NULL, " +
"action_type INTEGER NOT NULL, " +
"target_entity_id INTEGER NOT NULL, " +
"time_required_ms INTEGER NOT NULL" +
")"
);
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create pending_action table", e);
			}
			getSchema().setVersionNumber(1);
		}
		
		eventSystem = game.getSystem(EventSystem.class);
		worldSystem = game.getSystem(WorldSystem.class);
		entitySystem = game.getSystem(EntitySystem.class);
		entityTagSystem = game.getSystem(EntityTagSystem.class);
		uniqueTypeSystem = game.getSystem(UniqueTypeSystem.class);
		
		ACTION_MOVE = uniqueTypeSystem.getType("action_move");
		ACTION_ITEM_TAKE = uniqueTypeSystem.getType("action_item_take");
		ACTION_ITEM_DROP = uniqueTypeSystem.getType("action_item_drop");
	}
	
	public void queueAction(Entity actor, UniqueType actionType, DTime timeRequired, Entity targetEntity) throws DatabaseException {
		DTime currentTime = worldSystem.getCurrentTime();
		
		boolean isPlayer = entityTagSystem.getTagValue(actor, entitySystem.TAG_AVATAR, currentTime) != null;
		
		if (isPlayer) {
			worldSystem.incrementCurrentTime(timeRequired);
			log.log("Player %d action %s completed instantly (advanced time by %d s)", 
actor.getId(), actionType, timeRequired);
		} else {
			PendingAction existing = getPendingAction(actor);
			if (existing != null) {
				log.log("NPC %d already has pending action %s, skipping new %s", 
actor.getId(), existing.type, actionType);
				return;
			}
			
			long actionId = game.getNewGlobalId();
			
			try (PreparedStatement ps = game.db().prepareStatement(
"INSERT INTO pending_action (action_id, actor_id, action_type, target_entity_id, time_required_ms) VALUES (?, ?, ?, ?, ?)")) {
				ps.setLong(1, actionId);
				ps.setLong(2, actor.getId());
				ps.setLong(3, actionType.type());
				ps.setLong(4, targetEntity.getId());
				ps.setLong(5, timeRequired.toMilliseconds());
				ps.execute();
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create pending action", e);
			}
			
			eventSystem.addEvent(actionType, currentTime, new Reference(actionId, game));
			
			log.log("NPC %d queued action %s (id=%d, requires %d ms)", 
actor.getId(), actionType, actionId, timeRequired.toMilliseconds());
		}
	}
	
	public PendingAction getPendingAction(Entity actor) throws DatabaseException {
		DTime currentTime = worldSystem.getCurrentTime();
		
		try (PreparedStatement ps = game.db().prepareStatement(
"SELECT action_id, action_type, target_entity_id, time_required_ms, event.time " +
"FROM pending_action " +
"JOIN event ON event.reference = pending_action.action_id " +
"WHERE pending_action.actor_id = ? " +
"AND event.reference IN " + eventSystem.getValidEventsSubquery("pending_action.action_id") + 
" LIMIT 1")) {
			
			ps.setLong(1, actor.getId());
			eventSystem.setValidEventsSubqueryParameters(ps, 2, ACTION_MOVE, currentTime);
			
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					long actionId = rs.getLong("action_id");
					long actionTypeId = rs.getLong("action_type");
					long targetId = rs.getLong("target_entity_id");
					long timeRequiredMs = rs.getLong("time_required_ms");
					long createdAtMs = rs.getLong("time");
					
					UniqueType actionType = new UniqueType(actionTypeId, uniqueTypeSystem);
					
					return new PendingAction(actionId, actionType, DTime.fromMilliseconds(timeRequiredMs), targetId, DTime.fromMilliseconds(createdAtMs));
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Unable to query pending action", e);
		}
		
		return null;
	}
	
	public void clearPendingAction(Entity actor) throws DatabaseException {
		PendingAction action = getPendingAction(actor);
		if (action != null) {
			eventSystem.cancelEvent(new Reference(action.actionId, game));
			log.log("Cleared pending action %d for actor %d", action.actionId, actor.getId());
		}
	}
}
