package com.benleskey.textengine;

import com.benleskey.textengine.util.Message;

import java.util.Optional;

public class CommandInput extends Message<CommandInput> {
	public final static String M_ORIGINAL_LINE = "original_input_line";

	public static CommandInput make(String id) {
		return (new CommandInput()).id(id);
	}

	public static CommandInput makeNone() {
		return new CommandInput();
	}

	public CommandInput id(String id) {
		return this.put(M_INPUT_ID, id);
	}

	public CommandInput line(String line) {
		return this.put(M_ORIGINAL_LINE, line);
	}

	public Optional<String> getLine() {
		return this.getO(M_ORIGINAL_LINE);
	}

	public String getId() {
		return this.<String>getO(M_INPUT_ID).orElseThrow();
	}
}
