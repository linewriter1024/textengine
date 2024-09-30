package com.benleskey.textengine.model;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class RelationshipDescriptor {
	private Relationship relationship;
	private Entity provider;
	private Entity receiver;
	private UniqueType verb;
}
