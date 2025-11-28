package com.benleskey.textengine;

import com.benleskey.textengine.util.HookHandler;

public interface IGameSystem extends HookHandler {
	String getId();

	SchemaManager.Schema getSchema();
}
