package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.PluginEvent;

public interface OnStartClient extends PluginEvent
{
	void onStartClient(Client client);
}
