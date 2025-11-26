package com.benleskey.textengine.model;

import com.benleskey.textengine.systems.VisibilitySystem;
import lombok.Builder;
import lombok.Getter;

/**
 * Describes what an observer can see and at what distance.
 */
@Builder
@Getter
public class VisibilityDescriptor {
	private final Entity entity;
	private final Entity observer;
	private final VisibilitySystem.VisibilityLevel distanceLevel;
}
