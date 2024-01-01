package com.benleskey.textengine.commands;

import lombok.Getter;

import java.util.regex.Pattern;

@Getter
public class CommandVariant {
	private final String name;
	private final Pattern regex;
	private final CommandVariantFunction function;

	public CommandVariant(String name, String regex, CommandVariantFunction function) {
		this.name = name;
		this.regex = Pattern.compile(regex);
		this.function = function;
	}
}
