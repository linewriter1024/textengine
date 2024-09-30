package com.benleskey.textengine.model;

import com.benleskey.textengine.systems.UniqueTypeSystem;

public record UniqueType(long type, UniqueTypeSystem system) {
	@Override
	public String toString() {
		return String.format("UniqueType#%d#%s", type, system.getTypeLabel(this).orElse(""));
	}
}
