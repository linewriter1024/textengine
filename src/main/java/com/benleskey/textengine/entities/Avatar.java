package com.benleskey.textengine.entities;

import java.util.Optional;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.systems.AvatarBroadcastSystem;

public interface Avatar extends Actor {

    public default Optional<Client> getClient() {
        for (Client client : getGame().getClients()) {
            if (client.getEntity().isPresent() && client.getEntity().get().getId() == this.getId()) {
                return Optional.of(client);
            }
        }
        return Optional.empty();
    }

    @Override
    public default void onActionReady() {
        // Do nothing
    }

    @Override
    public default DTime getActionInterval() {
        return new DTime(1);
    }

    @Override
    public default void receiveBroadcast(CommandOutput output) {
        getGame().getSystem(AvatarBroadcastSystem.class)
                .deliverBroadcast(this, output);
    }
}
