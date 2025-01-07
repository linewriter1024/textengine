package com.benleskey.textengine.hooks.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.util.HookEvent;

public interface OnStartClient extends HookEvent
{
	void onStartClient(Client client);
}
