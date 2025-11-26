package com.benleskey.textengine.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Describes a connection between two places.
 */
@Builder
@Getter
public class ConnectionDescriptor {
	private final Entity from;
	private final Entity to;
	private final Relationship relationship;
}
