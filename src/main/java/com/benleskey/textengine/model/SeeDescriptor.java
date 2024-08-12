package com.benleskey.textengine.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder
@Getter
@EqualsAndHashCode
public class SeeDescriptor {
	private Entity seenEntity;
	private Look look;
	private String method;
}
