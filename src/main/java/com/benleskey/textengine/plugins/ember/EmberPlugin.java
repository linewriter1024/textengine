package com.benleskey.textengine.plugins.ember;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnPluginRegister;
import com.benleskey.textengine.plugins.ember.dice.EmberDicePlugin;

/**
 * EmberPlugin provides the West Marches-style game engine.
 * 
 * Registers sub-plugins for specific functionality like dice rolling.
 */
public class EmberPlugin extends Plugin implements OnPluginRegister {

    public EmberPlugin(Game game) {
        super(game);
    }

    @Override
    public void onPluginRegister() {
        // Register Ember dice plugin for rolling commands
        game.registerPlugin(new EmberDicePlugin(game));
    }
}
