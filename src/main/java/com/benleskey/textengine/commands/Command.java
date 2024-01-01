package com.benleskey.textengine.commands;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Getter
public class Command {
	private String name;
	private CommandFunction function;
	private Map<String, CommandVariant> variants = new HashMap<>();

	public Command(String name, CommandFunction function, CommandVariant... variants) {
		this.name = name;
		this.function = function;
		for(CommandVariant variant : variants) {
			this.variants.put(variant.getName(), variant);
		}
	}
}
