package com.benleskey.textengine.util;

import java.util.*;
import java.util.stream.Collectors;

public class Message<T> {
	public static final String M_TYPE = "type";
	public static final String M_RAW_ID = "raw";
	public static final String M_OUTPUT_ID = "output";
	public static final String M_INPUT_ID = "input";

	public Map<String, Object> values = new HashMap<>();

	protected Message() {
	}

	public Message(String type) {
		put(M_TYPE, type);
	}

	public static RawMessage make() {
		return new RawMessage();
	}

	public String getType() {
		return Objects.requireNonNull(this.get(M_TYPE), "message has no type: " + this);
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
		return "(" + s + ")";
	}

	@Override
	public String toString() {
		return toPrettyString();
	}

	public static class RawMessage extends Message<RawMessage> {
		public RawMessage() {
			super();
		}
	}
}
