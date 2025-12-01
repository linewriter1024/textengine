package com.benleskey.textengine.model;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.util.Logger;

/**
 * Base implementation of Entity that extends BaseReference.
 * All concrete entity classes should extend this class.
 */
public abstract class BaseEntity extends BaseReference implements Entity {
    protected final Logger log;

    public BaseEntity(long id, Game game) {
        super(id, game);
        // Create entity-specific logger with class name and ID prefix
        this.log = game.log.withPrefix(this.getClass().getSimpleName() + "#" + id);
    }

    @Override
    public UniqueType getEntityType() {
        return game.getUniqueTypeSystem().getType(this.getClass().getCanonicalName());
    }
}
