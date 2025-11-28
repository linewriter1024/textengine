package com.benleskey.textengine.util;

import lombok.Builder;
import lombok.Getter;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;

@Getter
public class Logger {
	private final PrintStream stream;
	private String prefix = "";

	@Builder
	private Logger(OutputStream stream) {
		this.stream = new PrintStream(stream);
	}

	public Logger withPrefix(String prefix) {
		Logger l = new Logger(this.stream);
		l.prefix = prefix;
		return l;
	}

	private String getFullPrefix() {
		return prefix.isEmpty() ? "" : "[" + prefix + "] ";
	}

	public void log(String fmt, Object... args) {
		stream.printf("[%s] %s%s\n", LocalDateTime.now(), getFullPrefix(), String.format(fmt, args));
	}

	public void logError(Throwable error) {
		stream.printf("Error: %s\n", error.getMessage());
		error.printStackTrace(stream);
	}
}
