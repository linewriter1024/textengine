package com.benleskey.textengine.cli;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.util.Markup;
import lombok.Builder;

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

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
		output.getText().ifPresent(text -> {
			// Convert markup to terminal output, using avatar's entity ID for you/notyou
			// conversion
			String avatarId = (entity != null) ? entity.map(e -> e.getKeyId()).orElse(null) : null;
			String rendered = Markup.toTerminal(Markup.raw(text), avatarId);
			System.out.println(rendered);
		});
	}

	@Override
	public void sendStreamedOutput(CommandOutput output, Flow.Publisher<String> stream,
			CompletableFuture<String> future) {
		stream.subscribe(new Flow.Subscriber<>() {
			private Flow.Subscription subscription;

			@Override
			public void onSubscribe(Flow.Subscription subscription) {
				this.subscription = subscription;
				subscription.request(1);
			}

			@Override
			public void onNext(String item) {
				System.out.print(item);
				System.out.flush();
				subscription.request(1);
			}

			@Override
			public void onError(Throwable throwable) {
				throwable.printStackTrace(System.err);
			}

			@Override
			public void onComplete() {
				System.out.println();
			}
		});

		future.join();
	}
}
