package com.benleskey.textengine.util;

/**
 * Utilities for handling markup in text output.
 * Supports HTML-like tags with proper escaping for safety.
 * 
 * Supported tags:
 * - <em>text</em> - emphasis (rendered as bold in terminal)
 * 
 * Escape sequences:
 * - &lt; - less than (<)
 * - &gt; - greater than (>)
 * - &amp; - ampersand (&)
 * - &quot; - double quote (")
 */
public class Markup {
	
	/**
	 * Represents markup text that is already escaped and safe to use.
	 * This type can only be created by escaping raw text or explicitly
	 * marking text as raw (which requires manual safety verification).
	 */
	public static class Safe {
		private final String content;
		
		private Safe(String content) {
			this.content = content;
		}
		
		public String getContent() {
			return content;
		}
		
		@Override
		public String toString() {
			return content;
		}
	}
	
	/**
	 * Escape raw text for safe inclusion in markup.
	 * Converts special characters to entity codes:
	 * - & -> &amp;
	 * - < -> &lt;
	 * - > -> &gt;
	 * - " -> &quot;
	 */
	public static Safe escape(String raw) {
		if (raw == null) {
			return new Safe("");
		}
		
		String escaped = raw
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;");
		
		return new Safe(escaped);
	}
	
	/**
	 * Mark text as raw/safe markup without escaping.
	 * USE WITH CAUTION: The caller is responsible for ensuring
	 * the text is properly escaped or trusted.
	 */
	public static Safe raw(String markup) {
		return new Safe(markup == null ? "" : markup);
	}
	
	/**
	 * Combine multiple safe markup fragments.
	 */
	public static Safe concat(Safe... parts) {
		StringBuilder sb = new StringBuilder();
		for (Safe part : parts) {
			if (part != null) {
				sb.append(part.content);
			}
		}
		return new Safe(sb.toString());
	}
	
	/**
	 * Create emphasized (bold) text.
	 */
	public static Safe em(String text) {
		return raw("<em>" + escape(text).content + "</em>");
	}
	
	/**
	 * Create an entity reference tag.
	 * Used for entities that should show as "you" when the player is the recipient.
	 * 
	 * Example: entity(123, "a goblin") -> <entity id="123">a goblin</entity>
	 * 
	 * When rendered for entity 123's player: "you"
	 * When rendered for others: "a goblin"
	 */
	public static Safe entity(long entityId, String description) {
		return raw("<entity id=\"" + entityId + "\">" + escape(description).content + "</entity>");
	}
	
	/**
	 * Create an entity reference tag using string ID.
	 */
	public static Safe entity(String entityId, String description) {
		return raw("<entity id=\"" + escape(entityId).content + "\">" + escape(description).content + "</entity>");
	}
	
	/**
	 * Create a verb with first/second person forms.
	 * 
	 * Example: verb("take", "takes") -> <you>take</you><notyou>takes</notyou>
	 * 
	 * When rendered for the actor: "take"
	 * When rendered for others: "takes"
	 */
	public static Safe verb(String firstPerson, String thirdPerson) {
		return raw("<you>" + escape(firstPerson).content + "</you><notyou>" + escape(thirdPerson).content + "</notyou>");
	}
	
	/**
	 * Common verb helper: present tense regular verb.
	 * Automatically adds 's' for third person.
	 * 
	 * Example: verb("take") -> <you>take</you><notyou>takes</notyou>
	 */
	public static Safe verb(String base) {
		return verb(base, base + "s");
	}
	
	/**
	 * Wrap content in a capital tag to capitalize the first letter when rendered.
	 * 
	 * Example: capital(entity("123", "player")) 
	 *   -> <capital><entity id="123">player</entity></capital>
	 *   -> renders as "You" or "Player" (first letter capitalized)
	 */
	public static Safe capital(Safe content) {
		return raw("<capital>" + content.content + "</capital>");
	}
	
	/**
	 * Unescape entity codes back to their original characters.
	 * Used when converting markup to plain text or rendering to terminal.
	 */
	public static String unescape(String text) {
		if (text == null) {
			return "";
		}
		
		return text
			.replace("&quot;", "\"")
			.replace("&gt;", ">")
			.replace("&lt;", "<")
			.replace("&amp;", "&");
	}
	
	/**
	 * Convert markup to terminal output with ANSI formatting.
	 * Processes tags like <em> and unescapes entities.
	 * 
	 * For avatar-specific rendering (you/notyou), pass the avatar's entity ID.
	 * If avatarEntityId is null, renders in third-person (observer view).
	 */
	public static String toTerminal(Safe markup, String avatarEntityId) {
		if (markup == null) {
			return "";
		}
		
		// Parse and process markup tags properly
		String result = processMarkup(markup.content, avatarEntityId);
		
		// Unescape HTML entities
		result = unescape(result);
		
		return result;
	}
	
	/**
	 * State for markup processing to track whether the last entity was the avatar.
	 */
	private static class MarkupState {
		boolean lastEntityWasAvatar = false;
	}
	
	/**
	 * Process markup tags with proper parsing (not regex).
	 * Handles: entity, you/notyou, em, capital tags.
	 */
	private static String processMarkup(String markup, String avatarEntityId) {
		MarkupState state = new MarkupState();
		return processMarkupWithState(markup, avatarEntityId, state);
	}
	
	/**
	 * Process markup tags with state tracking.
	 */
	private static String processMarkupWithState(String markup, String avatarEntityId, MarkupState state) {
		StringBuilder result = new StringBuilder();
		int i = 0;
		
		// ANSI escape codes
		String bold = "\033[1m";
		String reset = "\033[0m";
		
		while (i < markup.length()) {
			if (markup.charAt(i) == '<') {
				// Parse tag
				int closePos = markup.indexOf('>', i);
				if (closePos == -1) {
					// Malformed tag, just append rest
					result.append(markup.substring(i));
					break;
				}
				
				String tagContent = markup.substring(i + 1, closePos);
				boolean isClosingTag = tagContent.startsWith("/");
				String tagName = isClosingTag ? tagContent.substring(1) : tagContent.split(" ")[0];
				
				if (tagName.equals("entity")) {
					// Parse entity tag: <entity id="123">description</entity>
					String entityId = extractAttribute(tagContent, "id");
					int endTagPos = markup.indexOf("</entity>", closePos);
					if (endTagPos == -1) {
						i = closePos + 1;
						continue;
					}
					
					String description = markup.substring(closePos + 1, endTagPos);
					
					// Replace with "you" or description based on avatarEntityId
					if (avatarEntityId != null && entityId.equals(avatarEntityId)) {
						result.append("you");
						state.lastEntityWasAvatar = true; // Track that this entity was the avatar
					} else {
						result.append(description);
						state.lastEntityWasAvatar = false; // Track that this entity was NOT the avatar
					}
					
					i = endTagPos + "</entity>".length();
					
				} else if (tagName.equals("you")) {
					// Parse you/notyou pair: <you>take</you><notyou>takes</notyou>
					int youEndPos = markup.indexOf("</you>", closePos);
					if (youEndPos == -1) {
						i = closePos + 1;
						continue;
					}
					
					String youText = markup.substring(closePos + 1, youEndPos);
					
					// Find matching <notyou>
					int notyouStartPos = markup.indexOf("<notyou>", youEndPos);
					int notyouEndPos = markup.indexOf("</notyou>", notyouStartPos);
					
					if (notyouStartPos == -1 || notyouEndPos == -1) {
						i = youEndPos + "</you>".length();
						continue;
					}
					
					String notyouText = markup.substring(notyouStartPos + "<notyou>".length(), notyouEndPos);
					
					// Use appropriate text based on whether last entity was the avatar
					if (state.lastEntityWasAvatar) {
						result.append(youText);
					} else {
						result.append(notyouText);
					}
					
					i = notyouEndPos + "</notyou>".length();
					
				} else if (tagName.equals("em")) {
					// Emphasis: <em>text</em>
					result.append(bold);
					i = closePos + 1;
					
				} else if (tagName.equals("/em")) {
					result.append(reset);
					i = closePos + 1;
					
				} else if (tagName.equals("capital")) {
					// Capital: <capital>text</capital> - capitalize first letter of content
					int endTagPos = markup.indexOf("</capital>", closePos);
					if (endTagPos == -1) {
						i = closePos + 1;
						continue;
					}
					
					String capitalContent = markup.substring(closePos + 1, endTagPos);
					// Recursively process the content inside capital tag, passing state
					String processed = processMarkupWithState(capitalContent, avatarEntityId, state);
					// Capitalize first letter
					if (processed.length() > 0) {
						result.append(Character.toUpperCase(processed.charAt(0)));
						if (processed.length() > 1) {
							result.append(processed.substring(1));
						}
					}
					
					i = endTagPos + "</capital>".length();
					
				} else {
					// Unknown tag, skip it
					i = closePos + 1;
				}
				
			} else {
				// Regular character
				result.append(markup.charAt(i));
				i++;
			}
		}
		
		return result.toString();
	}
	
	/**
	 * Extract attribute value from tag content.
	 * Example: extractAttribute('entity id="123"', "id") -> "123"
	 */
	private static String extractAttribute(String tagContent, String attrName) {
		String pattern = attrName + "=\"";
		int startPos = tagContent.indexOf(pattern);
		if (startPos == -1) return "";
		
		startPos += pattern.length();
		int endPos = tagContent.indexOf("\"", startPos);
		if (endPos == -1) return "";
		
		return tagContent.substring(startPos, endPos);
	}
	
	/**
	 * Convert markup to terminal output (observer view - third person).
	 */
	public static String toTerminal(Safe markup) {
		return toTerminal(markup, null);
	}
	
	/**
	 * Convert markup to plain text by removing tags and unescaping entities.
	 */
	public static String toPlainText(Safe markup) {
		if (markup == null) {
			return "";
		}
		
		String text = markup.content;
		
		// Remove entity tags but keep descriptions
		text = text.replaceAll("<entity id=\"[^\"]*\">([^<]*)</entity>", "$1");
		
		// Handle you/notyou - keep third person (notyou) version for plain text
		text = text.replaceAll("<you>[^<]*</you><notyou>([^<]*)</notyou>", "$1");
		
		// Remove all remaining tags
		text = text.replaceAll("<[^>]+>", "");
		
		// Unescape entities
		text = unescape(text);
		
		return text;
	}
}
