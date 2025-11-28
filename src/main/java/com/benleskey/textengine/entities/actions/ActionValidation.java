package com.benleskey.textengine.entities.actions;

/**
 * Result of validating whether an action can be executed.
 * Contains success/failure status and optional error code + message.
 */
public class ActionValidation {
	private final boolean valid;
	private final String errorCode;
	private final String errorMessage;
	
	private ActionValidation(boolean valid, String errorCode, String errorMessage) {
		this.valid = valid;
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}
	
	/**
	 * Create a successful validation result.
	 */
	public static ActionValidation success() {
		return new ActionValidation(true, null, null);
	}
	
	/**
	 * Create a failed validation result with error code and message.
	 * 
	 * @param errorCode Machine-readable error code (e.g., "item_not_found")
	 * @param errorMessage Human-readable error message (e.g., "You can't find that item")
	 */
	public static ActionValidation failure(String errorCode, String errorMessage) {
		return new ActionValidation(false, errorCode, errorMessage);
	}
	
	public boolean isValid() {
		return valid;
	}
	
	public String getErrorCode() {
		return errorCode;
	}
	
	public String getErrorMessage() {
		return errorMessage;
	}
}
