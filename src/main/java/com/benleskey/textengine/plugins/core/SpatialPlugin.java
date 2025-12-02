package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.SpatialSystem;

import java.util.Set;

/**
 * Registers SpatialSystem after EventSystem so event-backed prepared statements
 * can be initialized cleanly in SpatialSystem.onSystemInitialize.
 */
public class SpatialPlugin extends Plugin implements OnPluginInitialize {

    public SpatialPlugin(Game game) {
        super(game);
    }

    @Override
    public Set<Plugin> getDependencies() {
        // Ensure EventPlugin (and thus EventSystem) is registered before SpatialSystem
        return Set.of(game.getPlugin(EventPlugin.class));
    }

    @Override
    public void onPluginInitialize() {
        // Register SpatialSystem only after EventSystem exists
        game.registerSystem(new SpatialSystem(game));
    }
}
