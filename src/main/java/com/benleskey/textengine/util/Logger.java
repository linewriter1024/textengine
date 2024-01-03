package com.benleskey.textengine.util;

import lombok.Builder;
import lombok.Getter;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;

@Getter
public class Logger {
	private final PrintStream stream;

	@Builder
	private Logger(OutputStream stream) {
		this.stream = new PrintStream(stream);
	}

	public void log(String fmt, Object... args) {
		stream.printf("[%s] %s\n", LocalDateTime.now(), String.format(fmt, args));
	}
}
