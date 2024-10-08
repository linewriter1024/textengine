package com.benleskey.textengine.commands;

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

	public CommandOutput text(String text) {
		return put(M_TEXT, text);
	}

	public CommandOutput error(String error) {
		return put(M_ERROR, error);
	}

	public CommandOutput textf(String fmt, Object... args) {
		return text(String.format(fmt, args));
	}

	public Optional<String> getText() {
		return getO(M_TEXT);
	}

	public Optional<String> getError() {
		return getO(M_ERROR);
	}
}
