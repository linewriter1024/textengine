package com.benleskey.textengine.hooks.core;

import com.benleskey.textengine.IGameSystem;
import com.benleskey.textengine.util.HookEvent;

public interface OnSystemInitialize extends HookEvent, IGameSystem {
	void onSystemInitialize();
}
