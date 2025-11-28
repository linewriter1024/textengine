package com.benleskey.textengine.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.ChatLanguageModel;

public class DialogGenerator {
	private final ChatLanguageModel model;

	public DialogGenerator(LlmProvider llmProvider) {
		this.model = llmProvider.getModelForPurpose(LlmProvider.Purpose.CHAT);
	}

	public static void main(String[] args) {
		DialogGenerator dialogGenerator = new DialogGenerator(new LlmProvider());
		System.out.println(dialogGenerator.getDialogFromDescription("A happy old gnome who speaks in riddles",
				"In an inn. Talking to Bob and Tim. Curious about Bob. Angry at Tim. Tim is rude.",
				"Tells Bob and Tim location of treasure in hidden cave called 'Cave de Prima' north by the old mill."));
	}

	public String getDialogFromDescription(String character, String context, String intent) {
		String systemPrompt = "Respond with a line of character dialog. You are a character described as 'character' in the following JSON. The context of the situation is given as 'context' in the following JSON. The intent you must express in your line of dialog is given as 'intent' in the following JSON. You must respond only with the line of dialog you as the character will say. No narration, only speech.";
		String json = Json.toJson(new DescriptionModel(character, context, intent));
		return model.chat(new SystemMessage(systemPrompt), new UserMessage(json)).aiMessage().text();
	}

	private record DescriptionModel(String character, String context, String intent) {
	}
}
