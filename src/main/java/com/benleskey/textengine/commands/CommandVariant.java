package com.benleskey.textengine.commands;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.regex.Pattern;

@Getter
public class CommandVariant {
	private String name;
	private Pattern regex;
	private CommandVariantFunction function;

	public CommandVariant(String name, String regex, CommandVariantFunction function) {
		this.name = name;
		this.regex = Pattern.compile(regex);
		this.function = function;
	}
}
