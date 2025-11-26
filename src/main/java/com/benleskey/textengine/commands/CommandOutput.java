package com.benleskey.textengine.commands;

import com.benleskey.textengine.util.Markup;
import com.benleskey.textengine.util.Message;

import java.util.Optional;

public class CommandOutput extends Message<CommandOutput> {
	public static final String M_TEXT = "text";
	public static final String M_ERROR = "error";
	public static final String M_UNKNOWN_COMMAND = "unknown_command";
	public static final String M_ORIGINAL_UNKNOWN_COMMAND_COMMAND = "unknown_command_original_command";
	public static final String M_ORIGINAL_UNKNOWN_COMMAND_LINE = "unknown_command_original_line";

	public CommandOutput() {
		super(M_OUTPUT_ID);
	}

	public static CommandOutput make(String id) {
		return (new CommandOutput()).put(M_OUTPUT_ID, id);
	}

	/**
	 * Set text output using safe markup.
	 * The Markup.Safe type ensures content is properly escaped.
	 * Use Markup.escape(text) for plain text or Markup.raw(markup) for markup.
	 */
	public CommandOutput text(Markup.Safe markup) {
		return put(M_TEXT, markup.getContent());
	}

	public CommandOutput error(String error) {
		return put(M_ERROR, error);
	}

	/**
	 * Set formatted text with automatic escaping.
	 * The format string is used as raw markup, but arguments are escaped.
	 * For backwards compatibility only - prefer using Markup.Safe directly.
	 */
	public CommandOutput textf(String fmt, Object... args) {
		// Escape all arguments
		Object[] escapedArgs = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			if (args[i] instanceof Markup.Safe) {
				escapedArgs[i] = ((Markup.Safe) args[i]).getContent();
			} else {
				escapedArgs[i] = Markup.escape(String.valueOf(args[i])).getContent();
			}
		}
		return put(M_TEXT, String.format(fmt, escapedArgs));
	}

	public Optional<String> getText() {
		return getO(M_TEXT);
	}

	public Optional<String> getError() {
		return getO(M_ERROR);
	}
}
