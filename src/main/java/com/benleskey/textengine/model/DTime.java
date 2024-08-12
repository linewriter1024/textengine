package com.benleskey.textengine.model;

public class DTime {
	private long raw;

	public DTime(long raw) {
		this.raw = raw;
	}

	public static DTime fromSeconds(long seconds) {
		return new DTime(seconds * 1000);
	}

	public static DTime fromSeconds(double seconds) {
		return new DTime((long)(seconds * 1000.0));
	}

	public static DTime fromMilliseconds(long milliseconds) {
		return new DTime(milliseconds);
	}

	public long toWholeSeconds() {
		return this.raw / 1000;
	}

	public double toSeconds() {
		return this.raw / 1000.0;
	}

	public long toMilliseconds() {
		return this.raw;
	}

	public long getRaw() {
		return raw;
	}

	public DTime add(DTime delta) {
		return new DTime(raw + delta.raw);
	}

	@Override
	public String toString() {
		return String.format("%.3f sec", toSeconds());
	}
}
