package com.benleskey.textengine.cli;

import com.benleskey.textengine.CommandInput;
import com.benleskey.textengine.CommandOutput;
import com.benleskey.textengine.Game;
import lombok.Builder;

import java.util.Scanner;

public class Client extends com.benleskey.textengine.Client {
	private Scanner scanner = new Scanner(System.in);
	private boolean apiDebug;

	@Builder
	private Client(Game game, boolean apiDebug) {
		this.game = game;
		this.apiDebug = apiDebug;
	}

	@Override
	public CommandInput waitForInput() {
		System.out.print("> ");
		if (scanner.hasNextLine()) {
			return game.inputLineToCommandInput(scanner.nextLine());
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
