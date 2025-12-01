package com.benleskey.textengine.model;

import com.benleskey.textengine.Game;

/**
 * Base implementation of Reference.
 * All concrete reference classes should extend this class.
 */
public class BaseReference implements Reference {
    protected final long id;
    protected final Game game;

    public BaseReference(long id, Game game) {
        this.id = id;
        this.game = game;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public Game getGame() {
        return game;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "#" + id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Reference reference))
            return false;
        return id == reference.getId();
    }
}
