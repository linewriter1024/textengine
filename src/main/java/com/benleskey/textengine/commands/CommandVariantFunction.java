package com.benleskey.textengine.commands;

import java.util.regex.Matcher;

public interface CommandVariantFunction {
	CommandInput run(Matcher args);
}
