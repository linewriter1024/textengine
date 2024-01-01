package com.benleskey.textengine.util;

public class VersionNumber {
	private final int major;
	private final int minor;
	private final int patch;

	public VersionNumber(int major, int minor, int patch) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	@Override
	public String toString() {
		return String.format("%d.%d.%d", major, minor, patch);
	}
}
