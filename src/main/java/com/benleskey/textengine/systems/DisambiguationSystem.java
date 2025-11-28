package com.benleskey.textengine.systems;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.util.Markup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * System for managing client-specific numeric ID disambiguation.
 * Also handles keyword extraction and highlighting for consistent presentation.
 * 
 * Assigns sequential numeric IDs to entities (items, exits, etc.) to allow
 * users to reference them by number instead of using potentially ambiguous
 * keywords.
 * 
 * The mapping is client-specific and resets with each new command that
 * generates
 * a list (look, inventory, etc.).
 */
public class DisambiguationSystem extends SingletonGameSystem {

	// Error codes
	public static final String ERR_AMBIGUOUS = "ambiguous";

	public DisambiguationSystem(Game game) {
		super(game);
	}

	/**
	 * Result of building a disambiguated list of entities.
	 * Contains the formatted markup parts, numeric ID mapping, and extracted
	 * keywords.
	 */
	public static class DisambiguatedList {
		private final List<Markup.Safe> markupParts;
		private final Map<Integer, Entity> numericIdMap;
		private final Map<Entity, String> keywords;

		public DisambiguatedList(List<Markup.Safe> markupParts,
				Map<Integer, Entity> numericIdMap,
				Map<Entity, String> keywords) {
			this.markupParts = markupParts;
			this.numericIdMap = numericIdMap;
			this.keywords = keywords;
		}

		public List<Markup.Safe> getMarkupParts() {
			return markupParts;
		}

		public Map<Integer, Entity> getNumericIdMap() {
			return numericIdMap;
		}

		public Map<Entity, String> getKeywords() {
			return keywords;
		}
	}

	/**
	 * Extract keyword(s) from a description for fuzzy matching.
	 * Since fuzzy matching supports substring matching, we can just return the full
	 * description.
	 * This allows users to match on ANY word in the description.
	 * 
	 * @param description The full description (e.g., "a smooth pebble", "peaceful
	 *                    meadow with wildflowers")
	 * @return The full description for fuzzy matching
	 */
	public String extractKeyword(String description) {
		return description;
	}

	/**
	 * Highlight a significant word within a description using <em> markup.
	 * Chooses the first word that's not an article (a, an, the) to highlight.
	 * 
	 * @param description The full description
	 * @return Description with one word wrapped in <em>tags</em>
	 */
	public String highlightKeyword(String description) {
		if (description == null || description.isEmpty()) {
			return description;
		}

		// Find first word that's not an article to highlight
		String[] articles = { "a ", "an ", "the ", "some " };
		String remaining = description;
		int offset = 0;

		// Skip past articles
		for (String article : articles) {
			if (remaining.toLowerCase().startsWith(article)) {
				offset += article.length();
				remaining = description.substring(offset);
			}
		}

		// Find the end of the first word (space or end of string)
		int wordEnd = remaining.indexOf(' ');
		if (wordEnd == -1) {
			wordEnd = remaining.length();
		}

		if (wordEnd == 0) {
			return description; // No word to highlight
		}

		// Build highlighted version
		String before = description.substring(0, offset);
		String word = remaining.substring(0, wordEnd);
		String after = remaining.substring(wordEnd);

		return before + "<em>" + word + "</em>" + after;
	}

	/**
	 * Build a disambiguated list with numeric IDs for all entities.
	 * Extracts keywords, highlights them, and assigns IDs.
	 * 
	 * @param entities             The list of entities to display
	 * @param descriptionExtractor Function to extract plain text description from
	 *                             each entity
	 * @param <T>                  The type of entity
	 * @return DisambiguatedList containing formatted markup, ID mapping, and
	 *         keywords
	 */
	public <T extends Entity> DisambiguatedList buildDisambiguatedList(
			List<T> entities,
			Function<T, String> descriptionExtractor) {

		List<Markup.Safe> markupParts = new java.util.ArrayList<>();
		Map<Integer, Entity> numericIdMap = new HashMap<>();
		Map<Entity, String> keywords = new HashMap<>();

		int numericId = 1;
		for (T entity : entities) {
			String description = descriptionExtractor.apply(entity);
			if (description == null)
				continue;

			// Extract keyword (full description) and highlight first significant word
			String keyword = extractKeyword(description);
			String highlighted = highlightKeyword(description);

			// Store keyword (full description) for fuzzy matching
			keywords.put(entity, keyword);

			// Store in numeric ID map
			numericIdMap.put(numericId, entity);

			// Format: highlighted_description [ID]
			markupParts.add(Markup.concat(
					Markup.raw(highlighted),
					Markup.raw(" ["),
					Markup.escape(String.valueOf(numericId)),
					Markup.raw("]")));

			numericId++;
		}

		return new DisambiguatedList(markupParts, numericIdMap, keywords);
	}

	/**
	 * Store the numeric ID mapping in a client.
	 * This allows the client to reference entities by number in subsequent
	 * commands.
	 * 
	 * @param client       The client to update
	 * @param numericIdMap The mapping of IDs to entities
	 */
	public void setClientMapping(Client client, Map<Integer, Entity> numericIdMap) {
		client.setNumericIdMap(numericIdMap);
	}

	/**
	 * Result of attempting to resolve an entity from user input.
	 * Can be a unique match, ambiguous (multiple matches), or not found.
	 */
	public static class ResolutionResult<T extends Entity> {
		private final T uniqueMatch;
		private final List<T> ambiguousMatches;
		private final ResultType resultType;

		private enum ResultType {
			UNIQUE, AMBIGUOUS, NOT_FOUND
		}

		private ResolutionResult(T uniqueMatch, List<T> ambiguousMatches, ResultType resultType) {
			this.uniqueMatch = uniqueMatch;
			this.ambiguousMatches = ambiguousMatches;
			this.resultType = resultType;
		}

		public static <T extends Entity> ResolutionResult<T> unique(T entity) {
			return new ResolutionResult<>(entity, List.of(), ResultType.UNIQUE);
		}

		public static <T extends Entity> ResolutionResult<T> ambiguous(List<T> matches) {
			// Use first match as placeholder for uniqueMatch field (won't be accessed)
			@SuppressWarnings("null")
			T placeholder = matches.isEmpty() ? null : matches.get(0);
			return new ResolutionResult<>(placeholder, matches, ResultType.AMBIGUOUS);
		}

		@SuppressWarnings("null")
		public static <T extends Entity> ResolutionResult<T> notFound() {
			// Use null placeholder - won't be accessed when resultType is NOT_FOUND
			return new ResolutionResult<>(null, List.of(), ResultType.NOT_FOUND);
		}

		public boolean isUnique() {
			return resultType == ResultType.UNIQUE;
		}

		public boolean isAmbiguous() {
			return resultType == ResultType.AMBIGUOUS;
		}

		public boolean isNotFound() {
			return resultType == ResultType.NOT_FOUND;
		}

		public T getUniqueMatch() {
			if (resultType != ResultType.UNIQUE) {
				throw new IllegalStateException("Cannot get unique match when result is " + resultType);
			}
			return uniqueMatch;
		}

		public List<T> getAmbiguousMatches() {
			if (resultType != ResultType.AMBIGUOUS) {
				throw new IllegalStateException("Cannot get ambiguous matches when result is " + resultType);
			}
			return ambiguousMatches;
		}
	}

	/**
	 * Try to resolve user input as either a numeric ID or fuzzy keyword match.
	 * First checks if input is a number and maps to an entity.
	 * If not, falls back to fuzzy matching by extracting keywords from
	 * descriptions.
	 * 
	 * Returns a ResolutionResult that indicates:
	 * - Unique match (exactly one entity matched)
	 * - Ambiguous (multiple entities matched - need user to clarify with numeric
	 * ID)
	 * - Not found (no entities matched)
	 * 
	 * @param client               The client making the request
	 * @param userInput            The user's input string
	 * @param candidates           List of candidate entities to match against
	 * @param descriptionExtractor Function to extract plain text description from
	 *                             each entity
	 * @param <T>                  The type of entity
	 * @return ResolutionResult indicating the outcome
	 */
	public <T extends Entity> ResolutionResult<T> resolveEntityWithAmbiguity(
			Client client,
			String userInput,
			List<T> candidates,
			Function<T, String> descriptionExtractor) {

		// Try to parse as numeric disambiguation ID (1, 2, 3...) first
		try {
			int numericId = Integer.parseInt(userInput);
			var entityOpt = client.getEntityByNumericId(numericId);
			if (entityOpt.isPresent() && candidates.contains(entityOpt.get())) {
				@SuppressWarnings("unchecked")
				T entity = (T) entityOpt.get();
				return ResolutionResult.unique(entity);
			}
		} catch (NumberFormatException e) {
			// Not a number, will check for # prefix or do fuzzy match below
		}

		// Try to parse as entity ID with # prefix (#1234)
		if (userInput.startsWith("#")) {
			try {
				long entityId = Long.parseLong(userInput.substring(1));
				EntitySystem entitySystem = game.getSystem(EntitySystem.class);
				Entity entityById = entitySystem.get(entityId);
				if (entityById != null && candidates.contains(entityById)) {
					@SuppressWarnings("unchecked")
					T entity = (T) entityById;
					return ResolutionResult.unique(entity);
				}
			} catch (NumberFormatException e) {
				// Invalid entity ID format, will do fuzzy match below
			}
		}

		// Fall back to fuzzy matching using extracted keywords
		List<T> matches = com.benleskey.textengine.util.FuzzyMatcher.findAllMatches(
				userInput, candidates, entity -> {
					String description = descriptionExtractor.apply(entity);
					return description != null ? extractKeyword(description) : null;
				});

		if (matches.isEmpty()) {
			return ResolutionResult.notFound();
		} else if (matches.size() == 1) {
			return ResolutionResult.unique(matches.get(0));
		} else {
			return ResolutionResult.ambiguous(matches);
		}
	}

	/**
	 * Try to resolve user input as either a numeric ID or fuzzy keyword match.
	 * First checks if input is a number and maps to an entity.
	 * If not, falls back to fuzzy matching by extracting keywords from
	 * descriptions.
	 * 
	 * @param client               The client making the request
	 * @param userInput            The user's input string
	 * @param candidates           List of candidate entities to match against
	 * @param descriptionExtractor Function to extract plain text description from
	 *                             each entity
	 * @param <T>                  The type of entity
	 * @return The matched entity, or null if not found or ambiguous
	 */
	@SuppressWarnings("null")
	public <T extends Entity> T resolveEntity(
			Client client,
			String userInput,
			List<T> candidates,
			Function<T, String> descriptionExtractor) {

		ResolutionResult<T> result = resolveEntityWithAmbiguity(client, userInput, candidates, descriptionExtractor);
		return result.isUnique() ? result.getUniqueMatch() : null;
	}

	/**
	 * Send a disambiguation prompt to the client when multiple entities match user
	 * input.
	 * Updates the client's numeric ID map and sends a formatted "Which X did you
	 * mean?" message.
	 * 
	 * @param client               The client to send the message to
	 * @param commandId            The command ID for the output message
	 * @param userInput            The original user input that was ambiguous
	 * @param matches              The list of matching entities
	 * @param descriptionExtractor Function to extract description from each entity
	 * @param <T>                  The type of entity
	 */
	public <T extends Entity> void sendDisambiguationPrompt(
			Client client,
			String commandId,
			String userInput,
			List<T> matches,
			Function<T, String> descriptionExtractor) {

		// Build disambiguated list with numeric IDs
		DisambiguatedList list = buildDisambiguatedList(matches, descriptionExtractor);

		// Update client's numeric ID map so they can use numbers
		client.setNumericIdMap(list.getNumericIdMap());

		// Format output
		java.util.List<Markup.Safe> parts = new java.util.ArrayList<>();
		parts.add(Markup.raw("Which "));
		parts.add(Markup.em(userInput));
		parts.add(Markup.raw(" did you mean? "));

		List<Markup.Safe> itemParts = list.getMarkupParts();
		for (int i = 0; i < itemParts.size(); i++) {
			if (i > 0) {
				if (i == itemParts.size() - 1) {
					parts.add(Markup.raw(", or "));
				} else {
					parts.add(Markup.raw(", "));
				}
			}
			parts.add(itemParts.get(i));
		}
		parts.add(Markup.raw("?"));

		client.sendOutput(CommandOutput.make(commandId)
				.error(ERR_AMBIGUOUS)
				.text(Markup.concat(parts.toArray(new Markup.Safe[0]))));
	}
}
