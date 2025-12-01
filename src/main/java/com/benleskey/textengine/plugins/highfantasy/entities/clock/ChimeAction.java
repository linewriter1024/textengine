package com.benleskey.textengine.plugins.highfantasy.entities.clock;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.model.Action;
import com.benleskey.textengine.model.ActionValidation;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.BroadcastSystem;
import com.benleskey.textengine.util.Markup;

/**
 * Action for a clock chiming on the hour.
 * Each chime action represents a single BONG, with properties for which
 * chime number this is (1-N) and total chimes (N).
 */
public class ChimeAction extends Action {

    public static final String BROADCAST_CHIME = "clock_chime";

    public ChimeAction(long id, Game game) {
        super(id, game);
    }

    @Override
    public UniqueType getActionType() {
        return GrandfatherClock.ACTION_CHIME;
    }

    @Override
    public ActionValidation canExecute() {
        // Clock can always chime
        return ActionValidation.success();
    }

    /**
     * Get which chime number this is (1 to totalChimes).
     */
    public int getChimeNumber() {
        return getLongProperty(GrandfatherClock.PROP_CHIME_NUMBER).orElse(1L).intValue();
    }

    /**
     * Set which chime number this is.
     */
    public void setChimeNumber(int number) {
        setLongProperty(GrandfatherClock.PROP_CHIME_NUMBER, number);
    }

    /**
     * Get total number of chimes for this hour.
     */
    public int getTotalChimes() {
        return getLongProperty(GrandfatherClock.PROP_TOTAL_CHIMES).orElse(1L).intValue();
    }

    /**
     * Set total number of chimes for this hour.
     */
    public void setTotalChimes(int total) {
        setLongProperty(GrandfatherClock.PROP_TOTAL_CHIMES, total);
    }

    @Override
    public CommandOutput execute() {
        BroadcastSystem bs = game.getSystem(BroadcastSystem.class);

        Entity actor = (Entity) getActor().orElseThrow();

        int chimeNumber = getChimeNumber();
        int totalChimes = getTotalChimes();

        CommandOutput broadcast = CommandOutput.make(BROADCAST_CHIME)
                .put("entity_id", actor.getKeyId())
                .put("chime_number", chimeNumber)
                .put("total_chimes", totalChimes)
                .text(Markup.escape(String.format("The grandfather clock chimes %d/%d. BONG",
                        chimeNumber, totalChimes)));

        bs.broadcast(actor, broadcast);

        // If this is the last chime, queue the next wait action
        if (chimeNumber == totalChimes) {
            GrandfatherClock clock = (GrandfatherClock) actor;
            clock.queueNextWait();
        }

        return broadcast;
    }

    @Override
    public String getDescription() {
        return "chiming";
    }
}
