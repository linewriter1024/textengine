package com.benleskey.textengine.entities.actions;

import com.benleskey.textengine.commands.CommandOutput;

/**
 * Result of validating whether an action can be executed.
 * Contains success/failure status and optional CommandOutput for player-facing errors.
 */
public class ActionValidation {
	private final boolean valid;
	private final CommandOutput errorOutput;
	
	private ActionValidation(boolean valid, CommandOutput errorOutput) {
		this.valid = valid;
		this.errorOutput = errorOutput;
	}
	
	/**
	 * Create a successful validation result.
	 */
	public static ActionValidation success() {
		return new ActionValidation(true, null);
	}
	
	/**
	 * Create a failed validation result with user-facing CommandOutput.
	 * The CommandOutput should include error code and user-friendly text.
	 * 
	 * @param errorOutput CommandOutput with error code and text for the player
	 */
	public static ActionValidation failure(CommandOutput errorOutput) {
		return new ActionValidation(false, errorOutput);
	}
	
	public boolean isValid() {
		return valid;
	}
	
	/**
	 * Get the error output to send to the client.
	 * Only present if validation failed.
	 */
	public CommandOutput getErrorOutput() {
		return errorOutput;
	}
	
	/**
	 * Get error code from the error output.
	 * Returns null if valid or if error output has no error field.
	 */
	public String getErrorCode() {
		if (errorOutput == null) {
			return null;
		}
		return errorOutput.getO(CommandOutput.M_ERROR).map(Object::toString).orElse(null);
	}
}
