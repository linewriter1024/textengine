package com.benleskey.textengine.systems;

import java.util.concurrent.atomic.AtomicReference;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.entities.Avatar;
import com.benleskey.textengine.hooks.core.OnCoreSystemsReady;
import com.benleskey.textengine.hooks.core.OnCreatePlayerEntity;
import com.benleskey.textengine.hooks.core.OnNewPlayerEntity;
import com.benleskey.textengine.hooks.core.OnStartClient;

public class ClientStartSystem extends SingletonGameSystem
        implements OnStartClient, OnCoreSystemsReady {

    private GrouplessPropertiesSubSystem<String, Long> clientAvatars;
    private EntitySystem entitySystem;

    public ClientStartSystem(Game game) {
        super(game);

        clientAvatars = game.registerSystem(new GrouplessPropertiesSubSystem<>(game, "client_avatars",
                PropertiesSubSystem.stringHandler(), PropertiesSubSystem.longHandler()));
    }

    @Override
    public void onCoreSystemsReady() {
        entitySystem = game.getSystem(EntitySystem.class);
    }

    @Override
    public void onStartClient(Client client) {
        client.setEntity(findOrCreatePlayerEntity(client));
    }

    Avatar findOrCreatePlayerEntity(Client client) {
        Avatar avatar = clientAvatars.get(client.getAccountIdentifier()).map(id -> entitySystem.get(id))
                .map(entity -> (Avatar) entity)
                .orElseGet(() -> createNewPlayerEntity(client));
        clientAvatars.insertIfAbsent(client.getAccountIdentifier(), avatar.getId());
        return avatar;
    }

    Avatar createNewPlayerEntity(Client client) {
        AtomicReference<Avatar> avatar = new AtomicReference<>();
        game.doHookEvent(OnCreatePlayerEntity.class, h -> h.onCreatePlayerEntity(client, avatar));
        game.doHookEvent(OnNewPlayerEntity.class, h -> h.onNewPlayerEntity(avatar.get(), client));
        return avatar.get();
    }
}
