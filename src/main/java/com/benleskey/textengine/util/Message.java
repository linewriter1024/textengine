package com.benleskey.textengine.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class Message<T> {
	public static final String M_OUTPUT_ID = "output";
	public static final String M_INPUT_ID = "input";

	public Map<String, Object> values = new HashMap<>();

	public static RawMessage make() {
		return new RawMessage();
	}

	public T put(String key, Object value) {
		values.put(key, value);
		return (T) this;
	}

	public <R> Optional<R> getO(String key) {
		return values.containsKey(key) ? Optional.of((R) values.get(key)) : Optional.empty();
	}

	public <R> R get(String key) {
		return this.<R>getO(key).orElseThrow();
	}

	public String toPrettyString() {
		StringJoiner s = new StringJoiner(", ");
		for (String key : values.keySet().stream().sorted().collect(Collectors.toList())) {
			Object value = values.get(key);
			if (value instanceof Message) {
				s.add(String.format("%s: %s", key, ((Message) value).toPrettyString()));
			} else {
				s.add(String.format("%s: '%s'", key, value));
			}
		}
		return "(" + s.toString() + ")";
	}

	@Override
	public String toString() {
		return toPrettyString();
	}

	public static class RawMessage extends Message<RawMessage> {
	}
}
