package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.llm.LlmProvider;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.SubmissionPublisher;

public class Echo extends Plugin implements OnPluginInitialize {
	public static final String ECHO = "echo";
	public static final String CHAT = "chat";
	public static final String M_ECHO_TEXT = "echo_text";

	public Echo(Game game) {
		super(game);
	}

	@Override
	public void onPluginInitialize() {
		game.registerCommand(new Command(ECHO, (c, i) -> c.sendOutput(CommandOutput.make(ECHO).text(i.get(M_ECHO_TEXT))),
			new CommandVariant(ECHO, "^echo[^\\w]*(.*)$", args -> CommandInput.makeNone().put(M_ECHO_TEXT, args.group(1)))));

		game.registerCommand(new Command(CHAT, (c, i) -> {
				LlmProvider llmProvider = new LlmProvider();
				StreamingChatLanguageModel model = llmProvider.getStreamingModelForPurpose(LlmProvider.Purpose.CHAT);
				String text = i.get(M_ECHO_TEXT);
				SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
				CompletableFuture<String> future = new CompletableFuture<>();

				model.chat(text, new StreamingChatResponseHandler() {
					@Override
					public void onPartialResponse(String partialResponse) {
						publisher.submit(partialResponse);
					}

					@Override
					public void onCompleteResponse(ChatResponse completeResponse) {
						future.complete(completeResponse.aiMessage().text());
						publisher.close();
					}

					@Override
					public void onError(Throwable error) {
						log.logError(error);
					}
				});

				c.sendStreamedOutput(CommandOutput.make(CHAT).text(""), publisher, future);
			},
			new CommandVariant(CHAT, "^chat[^\\w]*(.*)$", args -> CommandInput.makeNone().put(M_ECHO_TEXT, args.group(1)))));
	}
}
