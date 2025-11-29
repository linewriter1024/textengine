package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.NameGenerationSystem;

/**
 * NameGenerationPlugin
 *
 * Registers the NameGenerationSystem and a convenience CLI command
 * for name generation. The plugin also demonstrates sample style
 * registrations (default, elvish, orcish) used by the generator.
 */
public class NameGenerationPlugin extends Plugin implements OnPluginInitialize {

    public NameGenerationPlugin(Game game) {
        super(game);
    }

    @Override
    public void onPluginInitialize() {
        game.registerSystem(new NameGenerationSystem(game));

    }
}
