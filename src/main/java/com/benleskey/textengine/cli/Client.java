package com.benleskey.textengine.cli;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import lombok.Builder;

import java.util.Scanner;

public class Client extends com.benleskey.textengine.Client {
	private final Scanner scanner = new Scanner(System.in);
	private final boolean apiDebug;

	@Builder
	private Client(Game game, boolean apiDebug) {
		this.game = game;
		this.apiDebug = apiDebug;
	}

	@Override
	public CommandInput waitForInput() {
		System.out.print("> ");
		CommandInput toServer;
		if (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.trim().isEmpty()) {
				// Recurse
				return waitForInput();
			} else {
				toServer = game.inputLineToCommandInput(line);
			}
		} else {
			toServer = CommandInput.make(M_QUIT_FROM_CLIENT);
		}

		if (apiDebug) {
			System.out.printf("> %s\n", toServer.toPrettyString());
		}

		return toServer;
	}

	@Override
	public void sendOutput(CommandOutput output) {
		if (apiDebug) {
			System.out.printf("< %s\n", output.toPrettyString());
		}
		output.getError().ifPresent(error -> System.out.printf("! %s\n", error));
		output.getText().ifPresent(System.out::println);
	}
}
