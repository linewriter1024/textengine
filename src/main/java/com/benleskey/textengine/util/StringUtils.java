package com.benleskey.textengine.util;

/**
 * Utility functions for string manipulation.
 */
public class StringUtils {
	
	/**
	 * Capitalize the first character of a string.
	 * Returns empty string if input is null or empty.
	 */
	public static String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str != null ? str : "";
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
