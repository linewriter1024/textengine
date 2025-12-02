package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.ClientStartSystem;

public class ClientStartPlugin extends Plugin implements OnPluginInitialize {

    public ClientStartPlugin(Game game) {
        super(game);
    }

    @Override
    public void onPluginInitialize() {
        game.registerSystem(new ClientStartSystem(game));
    }
}
