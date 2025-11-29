package com.benleskey.textengine.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.model.DTime;

public class Avatar extends Actor implements Acting {

    public Avatar(long id, Game game) {
        super(id, game);
    }

    public static Avatar create(Game game) {
        var entitySystem = game.getSystem(com.benleskey.textengine.systems.EntitySystem.class);
        var lookSystem = game.getSystem(com.benleskey.textengine.systems.LookSystem.class);
        var actorActionSystem = game.getSystem(com.benleskey.textengine.systems.ActorActionSystem.class);

        Avatar actor = entitySystem.add(Avatar.class);
        lookSystem.addLook(actor, "basic", "yourself");
        entitySystem.addTag(actor, entitySystem.TAG_ACTOR);
        entitySystem.addTag(actor, entitySystem.TAG_AVATAR);
        entitySystem.addTag(actor, actorActionSystem.TAG_ACTING);
        return actor;
    }

    @Override
    public void onActionReady() {
        // Do nothing.
    }

    @Override
    public DTime getActionInterval() {
        return new DTime(1);
    }

}
