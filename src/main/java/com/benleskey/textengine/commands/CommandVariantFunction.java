package com.benleskey.textengine.commands;

import com.benleskey.textengine.CommandInput;

import java.util.regex.Matcher;

public interface CommandVariantFunction {
	CommandInput run(Matcher args);
}
