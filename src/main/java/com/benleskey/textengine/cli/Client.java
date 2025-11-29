package com.benleskey.textengine.cli;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Completer;
import org.jline.reader.ParsedLine;
import org.jline.reader.Candidate;
import com.benleskey.textengine.systems.CommandCompletionSystem;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.util.Markup;

import lombok.Builder;

public class Client extends com.benleskey.textengine.Client {
	private final boolean apiDebug;

	private Terminal terminal = null;
	private LineReader reader = null;

	@Builder
	private Client(Game game, boolean apiDebug) {
		this.game = game;
		this.apiDebug = apiDebug;

		try {
			terminal = TerminalBuilder.builder().system(true).dumb(true).build();
		} catch (IOException e) {
			throw new InternalException("Failed to initialize terminal", e);
		}
		// Tab-completion provider using the CommandCompletionSystem.
		Completer commandVariantCompleter = new Completer() {
			@Override
			public void complete(LineReader reader, ParsedLine line, java.util.List<Candidate> candidates) {
				CommandCompletionSystem cc = game.getSystem(CommandCompletionSystem.class);
				int wi = line.wordIndex();
				String typed = line.word().toLowerCase();

				// Top-level completions for first word
				if (wi == 0) {
					for (String token : cc.getTopLevelTokens()) {
						if (typed.isEmpty() || token.toLowerCase().startsWith(typed)) {
							candidates.add(new Candidate(token));
						}
					}
					return;
				}

				// For argument-level completions: look up completions for the entire parsed
				// line
				java.util.List<String> words = line.words();
				if (words.size() > 0) {
					java.util.List<String> suggestions = cc.getCompletionsForLine(line.line(), wi);
					for (String s : suggestions) {
						if (typed.isEmpty() || s.toLowerCase().startsWith(typed)) {
							candidates.add(new Candidate(s));
						}
					}
					return;
				}
			}
		};

		reader = LineReaderBuilder.builder().terminal(terminal).completer(commandVariantCompleter).build();
	}

	@Override
	public CommandInput waitForInput() {

		String line = null;
		try {
			line = reader.readLine("> ");
		} catch (UserInterruptException e) {
			return waitForInput();
		} catch (EndOfFileException e) {
			return CommandInput.make(M_QUIT_FROM_CLIENT);
		}

		CommandInput toServer;

		if (line.trim().isEmpty()) {
			return waitForInput();
		} else {
			toServer = game.inputLineToCommandInput(line);
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

	@Override
	public void quitFromServer() {
		super.quitFromServer();
		// Close the terminal if it was opened
		if (terminal != null) {
			try {
				terminal.close();
			} catch (IOException e) {
				// Ignore
			}
		}
	}
}
