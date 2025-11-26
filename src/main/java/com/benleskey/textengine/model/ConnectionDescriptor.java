package com.benleskey.textengine.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Describes a connection between two places via a named exit.
 */
@Builder
@Getter
public class ConnectionDescriptor {
	private final Entity from;
	private final Entity to;
	private final String exitName;
	private final Relationship relationship;
}
