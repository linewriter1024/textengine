package com.benleskey.textengine.plugins.games.ember.systems;

import java.util.concurrent.atomic.AtomicReference;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.GameSystem;
import com.benleskey.textengine.entities.Avatar;
import com.benleskey.textengine.hooks.core.OnCoreSystemsReady;
import com.benleskey.textengine.hooks.core.OnCreatePlayerEntity;
import com.benleskey.textengine.plugins.games.ember.entities.PlayerCharacter;
import com.benleskey.textengine.systems.EntitySystem;

public class NewPlayerSystem extends GameSystem implements OnCreatePlayerEntity, OnCoreSystemsReady {

    private EntitySystem entitySystem;

    public NewPlayerSystem(Game game) {
        super(game);
    }

    @Override
    public void onCoreSystemsReady() {
        entitySystem = game.getSystem(EntitySystem.class);

        entitySystem.registerEntityType(PlayerCharacter.class);
    }

    @Override
    public void onCreatePlayerEntity(Client client, AtomicReference<Avatar> avatar) {
        avatar.set(PlayerCharacter.create(game));
    }

}
