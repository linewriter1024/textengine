package com.benleskey.textengine.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Interfaces {
	public static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
		Set<Class<?>> interfaces = new HashSet<>();
		while (clazz != null) {
			interfaces.addAll(Arrays.asList(clazz.getInterfaces()));
			clazz = clazz.getSuperclass();
		}
		return interfaces;
	}
}
