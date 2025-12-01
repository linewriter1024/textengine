package com.benleskey.textengine.plugins.highfantasy.entities.clock;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.model.Action;
import com.benleskey.textengine.model.ActionResult;
import com.benleskey.textengine.model.ActionValidation;
import com.benleskey.textengine.model.UniqueType;

/**
 * Action for a grandfather clock waiting until the next chime time.
 * When this action completes, it triggers the chiming sequence and queues
 * the next wait action.
 */
public class ClockWaitAction extends Action {

    public ClockWaitAction(long id, Game game) {
        super(id, game);
    }

    @Override
    public UniqueType getActionType() {
        return GrandfatherClock.ACTION_CLOCK_WAIT;
    }

    @Override
    public ActionValidation canExecute() {
        // Clock can always wait
        return ActionValidation.success();
    }

    @Override
    public ActionResult execute() {
        // When wait completes, trigger the chiming sequence on the clock
        GrandfatherClock clock = getTarget()
                .filter(e -> e instanceof GrandfatherClock)
                .map(e -> (GrandfatherClock) e)
                .orElse(null);

        if (clock != null) {
            clock.onWaitComplete();
        }
        return ActionResult.success();
    }

    @Override
    public String getDescription() {
        return "waiting";
    }
}
