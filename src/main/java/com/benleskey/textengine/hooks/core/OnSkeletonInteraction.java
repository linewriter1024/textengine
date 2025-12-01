package com.benleskey.textengine.hooks.core;

import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.util.HookEvent;

/**
 * Hook fired when an entity with TAG_SKELETON is about to be interacted with.
 * Plugins should implement this to populate skeleton entities with full
 * content.
 * 
 * Skeleton entities are placeholders that were created but not fully populated.
 * For example, a Place that was generated as a neighboring exit but hasn't been
 * visited yet - it exists but has no items, exits, or other content.
 * 
 * After all handlers have run, the TAG_SKELETON will be removed from the
 * entity.
 */
public interface OnSkeletonInteraction extends HookEvent {
    /**
     * Called when a skeleton entity is about to be interacted with.
     * Implementations should check the entity type and populate it if appropriate.
     * 
     * @param entity The skeleton entity that needs to be populated
     */
    void onSkeletonInteraction(Entity entity);
}
