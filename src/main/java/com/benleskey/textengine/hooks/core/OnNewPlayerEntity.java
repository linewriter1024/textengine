package com.benleskey.textengine.hooks.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.entities.Avatar;
import com.benleskey.textengine.util.HookEvent;

public interface OnNewPlayerEntity extends HookEvent {
    void onNewPlayerEntity(Avatar avatar, Client client);
}
