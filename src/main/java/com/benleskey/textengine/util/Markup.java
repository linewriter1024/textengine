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
		
		String text = markup.content;
		
		// ANSI escape codes
		String bold = "\033[1m";
		String reset = "\033[0m";
		
		// Process entity references - replace with "you" if matching avatar, otherwise use description
		if (avatarEntityId != null) {
			// Match <entity id="123">description</entity> where id matches avatarEntityId
			String pattern = "<entity id=\"" + avatarEntityId.replace("\"", "\\\"") + "\">([^<]*)</entity>";
			text = text.replaceAll(pattern, "you");
			
			// Remove non-matching entity tags but keep their descriptions
			text = text.replaceAll("<entity id=\"[^\"]*\">([^<]*)</entity>", "$1");
			
			// Handle you/notyou - keep "you" version
			text = text.replaceAll("<you>([^<]*)</you><notyou>[^<]*</notyou>", "$1");
		} else {
			// Observer view: remove entity tags but keep descriptions
			text = text.replaceAll("<entity id=\"[^\"]*\">([^<]*)</entity>", "$1");
			
			// Handle you/notyou - keep "notyou" version
			text = text.replaceAll("<you>[^<]*</you><notyou>([^<]*)</notyou>", "$1");
		}
		
		// Replace emphasis tags with ANSI codes
		text = text
			.replaceAll("<em>", bold)
			.replaceAll("</em>", reset);
		
		// Unescape entities
		text = unescape(text);
		
		return text;
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
