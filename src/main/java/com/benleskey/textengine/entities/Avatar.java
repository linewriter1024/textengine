package com.benleskey.textengine.entities;

import java.util.Optional;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.systems.AvatarBroadcastSystem;

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

    public Optional<Client> getClient() {
        for (Client client : game.getClients()) {
            if (client.getEntity().isPresent() && client.getEntity().get().getId() == this.getId()) {
                return Optional.of(client);
            }
        }
        return Optional.empty();
    }

    @Override
    public void onActionReady() {
        // Do nothing.
    }

    @Override
    public DTime getActionInterval() {
        return new DTime(1);
    }

    /**
     * Receive a broadcast event from another entity.
     * Relays the broadcast to this actor's client, if one exists.
     * With the new markup system, broadcasts use <entity> and <you>/<notyou> tags
     * that are processed client-side, so no filtering is needed.
     * 
     * @param output The broadcast output to relay
     */
    @Override
    public void receiveBroadcast(CommandOutput output) {
        game.getSystem(AvatarBroadcastSystem.class)
                .deliverBroadcast(this, output);
    }
}
