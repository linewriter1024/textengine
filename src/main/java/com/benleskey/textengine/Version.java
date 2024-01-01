package com.benleskey.textengine;

import com.benleskey.textengine.util.Message;
import com.benleskey.textengine.util.VersionNumber;

public class Version {
	public static final String M_INTERNAL_NAME = "internal_name";
	public static final String M_HUMAN_NAME = "human_name";
	public static final String M_VERSION_NUMBER = "version_name";
	public static final String M_URL = "url";

	public static final String internalName = "com.benleskey.textengine";
	public static final String humanName = "Text Engine";
	public static final VersionNumber versionNumber = new VersionNumber(0, 0, 1);
	public static final String url = "https://benleskey.com/aka/textengine";

	public static String toHumanString() {
		return String.format("%s <%s> %s", humanName, url, versionNumber);
	}

	public static Message toMessage() {
		return Message.make().put(M_INTERNAL_NAME, internalName).put(M_HUMAN_NAME, humanName).put(M_VERSION_NUMBER, versionNumber.toString()).put(M_URL, url);
	}
}
