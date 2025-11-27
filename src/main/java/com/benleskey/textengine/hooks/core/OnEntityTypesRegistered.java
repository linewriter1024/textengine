package com.benleskey.textengine.hooks.core;

import com.benleskey.textengine.util.HookEvent;

/**
 * Called after entity types have been registered.
 * Use this hook when you need to generate entities that depend on custom entity types
 * being registered (e.g., Tree, Axe, Rattle).
 * 
 * Execution order:
 * 1. OnPluginInitialize
 * 2. Systems initialize (OnSystemInitialize)
 * 3. OnCoreSystemsReady (register entity types here)
 * 4. OnEntityTypesRegistered (generate initial world here)
 * 5. OnStart
 */
public interface OnEntityTypesRegistered extends HookEvent {
	void onEntityTypesRegistered();
}
