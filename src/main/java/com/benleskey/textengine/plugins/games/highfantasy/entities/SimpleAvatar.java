package com.benleskey.textengine.plugins.games.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Avatar;
import com.benleskey.textengine.model.BaseEntity;

public class SimpleAvatar extends BaseEntity implements Avatar {
    public SimpleAvatar(long id, Game game) {
        super(id, game);
    }

    public static SimpleAvatar create(Game game) {
        var entitySystem = game.getSystem(com.benleskey.textengine.systems.EntitySystem.class);
        var lookSystem = game.getSystem(com.benleskey.textengine.systems.LookSystem.class);
        var actorActionSystem = game.getSystem(com.benleskey.textengine.systems.ActionSystem.class);

        SimpleAvatar actor = entitySystem.add(SimpleAvatar.class);
        lookSystem.addLook(actor, lookSystem.LOOK_BASIC, actor.toString());
        entitySystem.addTag(actor, entitySystem.TAG_AVATAR);
        entitySystem.addTag(actor, actorActionSystem.TAG_ACTING);
        return actor;
    }
}
