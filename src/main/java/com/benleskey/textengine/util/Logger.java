package com.benleskey.textengine.util;

import lombok.Builder;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;

public class Logger {
	private PrintStream stream;

	@Builder
	private Logger(OutputStream stream) {
		this.stream = new PrintStream(stream);
	}

	public void log(String fmt, Object... args) {
		stream.printf("[%s] %s\n", LocalDateTime.now().toString(), String.format(fmt, args));
	}
}
