package com.benleskey.textengine.commands;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.exceptions.InternalException;

public interface CommandFunction {
	void run(Client client, CommandInput input) throws InternalException;
}
