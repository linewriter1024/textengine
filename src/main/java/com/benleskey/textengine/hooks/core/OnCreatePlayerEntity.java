package com.benleskey.textengine.hooks.core;

import java.util.concurrent.atomic.AtomicReference;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.entities.Avatar;
import com.benleskey.textengine.util.HookEvent;

public interface OnCreatePlayerEntity extends HookEvent {
    void onCreatePlayerEntity(Client client, AtomicReference<Avatar> avatar);
}
