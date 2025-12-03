package com.benleskey.textengine.plugins.games.ember;

import java.util.Set;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.hooks.core.OnPluginRegister;
import com.benleskey.textengine.plugins.games.ember.dice.EmberDicePlugin;
import com.benleskey.textengine.plugins.games.ember.systems.NewPlayerSystem;
import com.benleskey.textengine.plugins.worldgen.glowgen.GlowgenPlugin;

/**
 * EmberPlugin provides the West Marches-style game engine.
 * 
 * Registers sub-plugins for specific functionality like dice rolling.
 */
public class EmberPlugin extends Plugin implements OnPluginRegister, OnPluginInitialize {

    public EmberPlugin(Game game) {
        super(game);
    }

    @Override
    public Set<Plugin> getDependencies() {
        return Set.of(game.getPlugin(GlowgenPlugin.class));
    }

    @Override
    public void onPluginRegister() {
        game.registerPlugin(new EmberDicePlugin(game));
    }

    @Override
    public void onPluginInitialize() {
        game.registerSystem(new NewPlayerSystem(game));
    }
}
