package com.benleskey.textengine.model;

/**
 * Result of executing an action.
 * Contains success/failure status.
 */
public class ActionResult {
    private final boolean success;

    private ActionResult(boolean success) {
        this.success = success;
    }

    /**
     * Create a successful result.
     */
    public static ActionResult success() {
        return new ActionResult(true);
    }

    /**
     * Create a failure result.
     */
    public static ActionResult failure() {
        return new ActionResult(false);
    }

    public boolean isSuccess() {
        return success;
    }
}
