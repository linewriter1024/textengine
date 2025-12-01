package com.benleskey.textengine.model;

import java.util.Optional;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Acting;
import com.benleskey.textengine.systems.ActionSystem;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.util.Logger;

/**
 * Base class for actions that actors (players and NPCs) can perform.
 * Actions are stored in the database and extend BaseReference for consistent ID
 * handling.
 * 
 * Action properties (actor, target, time required, etc.) are stored in a
 * flexible
 * key-value property table, allowing different action types to have different
 * properties.
 * 
 * Each action type must:
 * - Define an action type identifier (UniqueType)
 * - Implement execute() to perform the action
 * - Implement getDescription() for visibility to other entities
 * - Implement canExecute() to validate preconditions
 */
public abstract class Action extends BaseReference {
    protected final Logger log;

    public Action(long id, Game game) {
        super(id, game);
        this.log = game.log.withPrefix(this.getClass().getSimpleName() + "#" + id);
    }

    /**
     * Get the unique type identifying this action class.
     * Used to store/retrieve action type from database.
     * 
     * @return The action type
     */
    public abstract UniqueType getActionType();

    /**
     * Execute this action.
     * Called when the action's time requirement is met.
     * Actions should broadcast their state changes to nearby entities.
     * 
     * @return ActionResult indicating success or failure
     */
    public abstract ActionResult execute();

    /**
     * Check if this action can be executed.
     * Called before queueing and before execution to validate preconditions.
     * Examples: check if item is takeable, not too heavy, target still exists, etc.
     * 
     * @return ActionValidation result indicating if action can execute and why not
     *         if it can't
     */
    public abstract ActionValidation canExecute();

    /**
     * Get a human-readable description of this action for observers.
     * Used when other entities look at the actor performing this action.
     * Example: "moving north", "taking a sword", "dropping a shield"
     * 
     * @return Description of what the actor is doing
     */
    public abstract String getDescription();

    // ========== Property accessors delegating to ActionSystem ==========

    /**
     * Get a Reference property value for this action.
     * 
     * @param key   The property key (UniqueType)
     * @param clazz The Reference subclass to return
     * @return Optional containing the Reference, or empty if not set
     */
    protected <T extends Reference> Optional<T> getRefProperty(UniqueType key, Class<T> clazz) {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        return aas.getActionProperty(this, key)
                .map(id -> {
                    if (Entity.class.isAssignableFrom(clazz)) {
                        // Use EntitySystem.get(id) to look up the correct concrete type from database
                        @SuppressWarnings("unchecked")
                        T result = (T) game.getSystem(EntitySystem.class).get(id);
                        return result;
                    } else {
                        // Generic Reference
                        try {
                            return clazz.getDeclaredConstructor(long.class, Game.class).newInstance(id, game);
                        } catch (Exception e) {
                            throw new RuntimeException("Unable to create Reference of type " + clazz, e);
                        }
                    }
                });
    }

    /**
     * Set a Reference property value for this action.
     * 
     * @param key   The property key (UniqueType)
     * @param value The Reference value
     */
    protected void setRefProperty(UniqueType key, Reference value) {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        aas.setActionProperty(this, key, value.getId());
    }

    /**
     * Get a UniqueType property value for this action.
     * 
     * @param key The property key (UniqueType)
     * @return Optional containing the UniqueType, or empty if not set
     */
    protected Optional<UniqueType> getTypeProperty(UniqueType key) {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        return aas.getActionProperty(this, key)
                .map(id -> new UniqueType(id, game.getSystem(com.benleskey.textengine.systems.UniqueTypeSystem.class)));
    }

    /**
     * Set a UniqueType property value for this action.
     * 
     * @param key   The property key (UniqueType)
     * @param value The UniqueType value
     */
    protected void setTypeProperty(UniqueType key, UniqueType value) {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        aas.setActionProperty(this, key, value.type());
    }

    /**
     * Get a long property value for this action (for DTime, etc.).
     * 
     * @param key The property key (UniqueType)
     * @return Optional containing the long value, or empty if not set
     */
    protected Optional<Long> getLongProperty(UniqueType key) {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        return aas.getActionProperty(this, key);
    }

    /**
     * Set a long property value for this action (for DTime, etc.).
     * 
     * @param key   The property key (UniqueType)
     * @param value The long value
     */
    protected void setLongProperty(UniqueType key, long value) {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        aas.setActionProperty(this, key, value);
    }

    // ========== Standard property convenience methods ==========

    /**
     * Get the actor performing this action.
     * 
     * @return Optional containing the actor, or empty if not set
     */
    public Optional<Acting> getActor() {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        // All Acting entities are also Entity, so we query for Entity and cast
        return getRefProperty(aas.PROP_ACTOR, Entity.class)
                .filter(e -> e instanceof Acting)
                .map(e -> (Acting) e);
    }

    /**
     * Set the actor performing this action.
     * 
     * @param actor The actor
     */
    public void setActor(Acting actor) {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        // Acting extends Entity which extends Reference
        setRefProperty(aas.PROP_ACTOR, (Reference) actor);
    }

    /**
     * Get the target entity for this action.
     * 
     * @return Optional containing the target entity, or empty if not set
     */
    public Optional<Entity> getTarget() {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        return getRefProperty(aas.PROP_TARGET, Entity.class);
    }

    /**
     * Set the target entity for this action.
     * 
     * @param target The target entity
     */
    public void setTarget(Entity target) {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        setRefProperty(aas.PROP_TARGET, target);
    }

    /**
     * Get the time required for this action to complete.
     * 
     * @return Time required, or zero if not set
     */
    public DTime getTimeRequired() {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        return getLongProperty(aas.PROP_TIME_REQUIRED)
                .map(ms -> DTime.fromMilliseconds(ms))
                .orElse(DTime.fromMilliseconds(0));
    }

    /**
     * Set the time required for this action to complete.
     * 
     * @param timeRequired Time required
     */
    public void setTimeRequired(DTime timeRequired) {
        ActionSystem aas = game.getSystem(ActionSystem.class);
        setLongProperty(aas.PROP_TIME_REQUIRED, timeRequired.toMilliseconds());
    }
}
