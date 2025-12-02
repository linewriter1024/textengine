package com.benleskey.textengine.plugins.ember.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Avatar;
import com.benleskey.textengine.model.BaseEntity;

public class PlayerCharacter extends BaseEntity implements Avatar {

    public PlayerCharacter(long id, Game game) {
        super(id, game);
    }

    public static PlayerCharacter create(Game game) {
        var entitySystem = game.getSystem(com.benleskey.textengine.systems.EntitySystem.class);
        var lookSystem = game.getSystem(com.benleskey.textengine.systems.LookSystem.class);
        var actorActionSystem = game.getSystem(com.benleskey.textengine.systems.ActionSystem.class);

        PlayerCharacter actor = entitySystem.add(PlayerCharacter.class);
        lookSystem.addLook(actor, lookSystem.LOOK_BASIC, actor.toString());
        entitySystem.addTag(actor, entitySystem.TAG_AVATAR);
        entitySystem.addTag(actor, actorActionSystem.TAG_ACTING);
        return actor;
    }

}
