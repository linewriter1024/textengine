package com.benleskey.textengine.commands;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class Command {
	private final String name;
	private final CommandFunction function;
	private final Map<String, CommandVariant> variants = new HashMap<>();

	public Command(String name, CommandFunction function, CommandVariant... variants) {
		this.name = name;
		this.function = function;
		for (CommandVariant variant : variants) {
			this.variants.put(variant.getName(), variant);
		}
	}
}
