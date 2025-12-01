package com.benleskey.textengine.model;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class LookDescriptor {
	private Entity entity;
	private Look look;
	private UniqueType type;
	private String description;
}
