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
	 */
	public static String toTerminal(Safe markup) {
		if (markup == null) {
			return "";
		}
		
		String text = markup.content;
		
		// ANSI escape codes
		String bold = "\033[1m";
		String reset = "\033[0m";
		
		// Replace tags with ANSI codes
		text = text
			.replaceAll("<em>", bold)
			.replaceAll("</em>", reset);
		
		// Unescape entities
		text = unescape(text);
		
		return text;
	}
	
	/**
	 * Convert markup to plain text by removing tags and unescaping entities.
	 */
	public static String toPlainText(Safe markup) {
		if (markup == null) {
			return "";
		}
		
		String text = markup.content;
		
		// Remove all tags
		text = text.replaceAll("<[^>]+>", "");
		
		// Unescape entities
		text = unescape(text);
		
		return text;
	}
}
