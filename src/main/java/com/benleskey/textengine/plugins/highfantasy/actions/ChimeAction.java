package com.benleskey.textengine.plugins.highfantasy.actions;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.model.Action;
import com.benleskey.textengine.model.ActionValidation;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.plugins.highfantasy.GameCalendar;
import com.benleskey.textengine.plugins.highfantasy.HighFantasyPlugin;
import com.benleskey.textengine.systems.BroadcastSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

/**
 * Action for a clock chiming on the hour.
 * The clock entity performs this action when the hour changes.
 */
public class ChimeAction extends Action {

    public static final String BROADCAST_CHIME = "clock_chime";

    public ChimeAction(long id, Game game) {
        super(id, game);
    }

    @Override
    public UniqueType getActionType() {
        return HighFantasyPlugin.ACTION_CHIME;
    }

    @Override
    public ActionValidation canExecute() {
        // Clock can always chime
        return ActionValidation.success();
    }

    @Override
    public CommandOutput execute() {
        BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
        WorldSystem ws = game.getSystem(WorldSystem.class);

        Entity actor = (Entity) getActor().orElseThrow();

        // Get current hour
        GameCalendar.CalendarDate date = GameCalendar.fromDTime(ws.getCurrentTime());
        int currentHour = date.hour();

        // Calculate chime count (1-12)
        int chimeCount = currentHour % 12;
        if (chimeCount == 0)
            chimeCount = 12;

        String chimes = "BONG ".repeat(chimeCount).trim();
        CommandOutput broadcast = CommandOutput.make(BROADCAST_CHIME)
                .put("entity_id", actor.getKeyId())
                .put("chime_count", chimeCount)
                .text(Markup.escape(String.format("The grandfather clock chimes %d %s. %s",
                        chimeCount,
                        chimeCount == 1 ? "time" : "times",
                        chimes)));

        bs.broadcast(actor, broadcast);
        return broadcast;
    }

    @Override
    public String getDescription() {
        return "chiming";
    }
}
