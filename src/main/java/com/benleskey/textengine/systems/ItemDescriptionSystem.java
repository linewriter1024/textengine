package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;

import java.util.*;

/**
 * System for registering and retrieving item descriptions based on tags.
 * Plugins can register descriptions for specific tags (e.g., "This resource is abundant").
 */
public class ItemDescriptionSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	// Map from UniqueType tag to list of descriptions
	private final Map<UniqueType, List<String>> tagDescriptions = new HashMap<>();
	
	public ItemDescriptionSystem(Game game) {
		super(game);
	}
	
	@Override
	public void onSystemInitialize() {
		int v = getSchema().getVersionNumber();
		if (v == 0) {
			// No database tables needed - all in-memory
			getSchema().setVersionNumber(1);
		}
	}
	
	/**
	 * Register a description for a tag.
	 * Multiple descriptions can be registered for the same tag.
	 */
	public void registerTagDescription(UniqueType tag, String description) {
		tagDescriptions.computeIfAbsent(tag, k -> new ArrayList<>()).add(description);
		log.log("Registered description for tag: " + tag);
	}
	
	/**
	 * Get all descriptions for an item based on its tags.
	 * Returns a list of description strings.
	 */
	public List<String> getDescriptions(Entity item, DTime when) {
		List<String> descriptions = new ArrayList<>();
		EntityTagSystem tagSystem = game.getSystem(EntityTagSystem.class);
		
		// Get all tags for the item
		Set<UniqueType> itemTags = tagSystem.getTags(item, when);
		
		// Collect descriptions for each tag
		for (UniqueType tag : itemTags) {
			List<String> tagDescs = tagDescriptions.get(tag);
			if (tagDescs != null) {
				descriptions.addAll(tagDescs);
			}
		}
		
		return descriptions;
	}
	
	/**
	 * Get descriptions for specific tags on an item.
	 * Useful when you want descriptions for specific tags only.
	 */
	public List<String> getDescriptionsForTags(Entity item, DTime when, UniqueType... tags) {
		List<String> descriptions = new ArrayList<>();
		EntityTagSystem tagSystem = game.getSystem(EntityTagSystem.class);
		
		for (UniqueType tag : tags) {
			if (tagSystem.hasTag(item, tag, when)) {
				List<String> tagDescs = tagDescriptions.get(tag);
				if (tagDescs != null) {
					descriptions.addAll(tagDescs);
				}
			}
		}
		
		return descriptions;
	}
	
	/**
	 * Check if a tag has any descriptions registered.
	 */
	public boolean hasDescriptionsFor(UniqueType tag) {
		return tagDescriptions.containsKey(tag) && !tagDescriptions.get(tag).isEmpty();
	}
}
