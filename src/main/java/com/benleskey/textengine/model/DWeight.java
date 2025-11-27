package com.benleskey.textengine.model;

/**
 * Represents weight in grams.
 * One DWeight unit = 1 gram.
 */
public record DWeight(long raw) {

	public static DWeight fromGrams(long grams) {
		return new DWeight(grams);
	}

	public static DWeight fromGrams(double grams) {
		return new DWeight((long) grams);
	}

	public static DWeight fromKilograms(long kilograms) {
		return new DWeight(kilograms * 1000);
	}

	public static DWeight fromKilograms(double kilograms) {
		return new DWeight((long) (kilograms * 1000.0));
	}

	public long toGrams() {
		return this.raw;
	}

	public double toKilograms() {
		return this.raw / 1000.0;
	}

	public DWeight add(DWeight other) {
		return new DWeight(raw + other.raw);
	}

	public DWeight subtract(DWeight other) {
		return new DWeight(raw - other.raw);
	}

	public boolean isGreaterThan(DWeight other) {
		return this.raw > other.raw;
	}

	public boolean isLessThanOrEqual(DWeight other) {
		return this.raw <= other.raw;
	}

	@Override
	public String toString() {
		if (raw < 1000) {
			return raw + "g";
		} else {
			return String.format("%.2fkg", toKilograms());
		}
	}
}
