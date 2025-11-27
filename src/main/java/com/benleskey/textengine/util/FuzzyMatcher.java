package com.benleskey.textengine.util;

import java.util.List;
import java.util.function.Function;

/**
 * Utility for fuzzy string matching, commonly used for matching user input
 * against entity descriptions, exit names, item names, etc.
 * 
 * Supports matching against plain text or markup-containing strings.
 */
public class FuzzyMatcher {
	
	/**
	 * Match user input against a list of candidates using fuzzy matching.
	 * Returns the matched candidate if exactly one match is found, null otherwise.
	 * 
	 * Matching strategy:
	 * 1. First tries exact match (case-insensitive, markup-stripped)
	 * 2. Then tries substring match (case-insensitive, markup-stripped)
	 * 3. Returns null if zero or multiple matches found (ambiguous)
	 * 
	 * @param userInput The user's input string
	 * @param candidates List of candidate strings to match against
	 * @return The matched candidate, or null if no unique match found
	 */
	public static String match(String userInput, List<String> candidates) {
		if (candidates.isEmpty() || userInput == null) {
			return null;
		}
		
		String lowerInput = userInput.toLowerCase().trim();
		String matchedCandidate = null;
		int matchCount = 0;
		
		// First pass: try exact match
		for (String candidate : candidates) {
			// Strip markup for matching - we want "forest" to match "<em>dark forest</em>"
			String candidateStripped = Markup.toPlainText(Markup.raw(candidate)).toLowerCase();
			
			if (candidateStripped.equals(lowerInput)) {
				return candidate; // Exact match, use it immediately
			}
		}
		
		// Second pass: try substring match
		for (String candidate : candidates) {
			String candidateStripped = Markup.toPlainText(Markup.raw(candidate)).toLowerCase();
			
			if (candidateStripped.contains(lowerInput)) {
				matchedCandidate = candidate;
				matchCount++;
			}
		}
		
		// Return matched candidate only if unambiguous
		return matchCount == 1 ? matchedCandidate : null;
	}
	
	/**
	 * Match user input against a list of objects using fuzzy matching.
	 * Similar to match(String, List<String>) but works with any object type.
	 * 
	 * @param userInput The user's input string
	 * @param candidates List of candidate objects to match against
	 * @param nameExtractor Function to extract the matchable string from each candidate
	 * @param <T> The type of candidate objects
	 * @return The matched candidate object, or null if no unique match found
	 */
	@SuppressWarnings("null")
	public static <T> T match(String userInput, List<T> candidates, Function<T, String> nameExtractor) {
		if (candidates.isEmpty() || userInput == null) {
			return null;
		}
		
		String lowerInput = userInput.toLowerCase().trim();
		T matchedCandidate = null;
		int matchCount = 0;
		
		// First pass: try exact match
		for (T candidate : candidates) {
			String name = nameExtractor.apply(candidate);
			if (name == null) continue;
			
			// Strip markup for matching - we want "forest" to match "<em>dark forest</em>"
			String nameStripped = Markup.toPlainText(Markup.raw(name)).toLowerCase();
			
			if (nameStripped.equals(lowerInput)) {
				return candidate; // Exact match, use it immediately
			}
		}
		
		// Second pass: try substring match
		for (T candidate : candidates) {
			String name = nameExtractor.apply(candidate);
			if (name == null) continue;
			
			String nameStripped = Markup.toPlainText(Markup.raw(name)).toLowerCase();
			
			if (nameStripped.contains(lowerInput)) {
				matchedCandidate = candidate;
				matchCount++;
			}
		}
		
		// Return matched candidate only if unambiguous
		return matchCount == 1 ? matchedCandidate : null;
	}
	
	/**
	 * Check if user input matches a candidate string (fuzzy match).
	 * Returns true if the input is contained in the candidate (case-insensitive, markup-stripped).
	 * 
	 * @param userInput The user's input string
	 * @param candidate The candidate string to check against
	 * @return true if the input matches the candidate
	 */
	public static boolean matches(String userInput, String candidate) {
		if (userInput == null || candidate == null) {
			return false;
		}
		
		String lowerInput = userInput.toLowerCase().trim();
		// Strip markup for matching - we want "forest" to match "<em>dark forest</em>"
		String candidateStripped = Markup.toPlainText(Markup.raw(candidate)).toLowerCase();
		
		// Check exact match first
		if (candidateStripped.equals(lowerInput)) {
			return true;
		}
		
		// Then check substring match
		return candidateStripped.contains(lowerInput);
	}
	
	/**
	 * Find all matches for user input against a list of objects.
	 * Unlike match(), this returns ALL matches, not just unique ones.
	 * 
	 * @param userInput The user's input string
	 * @param candidates List of candidate objects to match against
	 * @param nameExtractor Function to extract the matchable string from each candidate
	 * @param <T> The type of candidate objects
	 * @return List of all matching candidates (empty if no matches)
	 */
	public static <T> List<T> findAllMatches(String userInput, List<T> candidates, Function<T, String> nameExtractor) {
		List<T> matches = new java.util.ArrayList<>();
		
		if (candidates.isEmpty() || userInput == null) {
			return matches;
		}
		
		String lowerInput = userInput.toLowerCase().trim();
		
		// First pass: collect exact matches
		for (T candidate : candidates) {
			String name = nameExtractor.apply(candidate);
			if (name == null) continue;
			
			String nameStripped = Markup.toPlainText(Markup.raw(name)).toLowerCase();
			
			if (nameStripped.equals(lowerInput)) {
				matches.add(candidate);
			}
		}
		
		// If we have exact matches, return those
		if (!matches.isEmpty()) {
			return matches;
		}
		
		// Second pass: collect substring matches
		for (T candidate : candidates) {
			String name = nameExtractor.apply(candidate);
			if (name == null) continue;
			
			String nameStripped = Markup.toPlainText(Markup.raw(name)).toLowerCase();
			
			if (nameStripped.contains(lowerInput)) {
				matches.add(candidate);
			}
		}
		
		return matches;
	}
}
