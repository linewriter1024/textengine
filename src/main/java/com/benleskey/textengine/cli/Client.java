package com.benleskey.textengine.cli;

import com.benleskey.textengine.CommandInput;
import com.benleskey.textengine.CommandOutput;
import com.benleskey.textengine.Game;
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
		if (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.trim().isEmpty()) {
				return waitForInput();
			} else {
				CommandInput input = game.inputLineToCommandInput(line);
				if (apiDebug) {
					System.out.printf("> %s\n", input.toPrettyString());
				}
				return input;
			}
		} else {
			return CommandInput.make(M_QUIT_FROM_CLIENT);
		}
	}

	@Override
	public void sendOutput(CommandOutput output) {
		if (apiDebug) {
			System.out.printf("< %s\n", output.toPrettyString());
		}
		output.getText().ifPresent(text -> System.out.println(text));
	}
}
