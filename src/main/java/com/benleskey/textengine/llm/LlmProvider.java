package com.benleskey.textengine.llm;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;

import java.util.Set;

public class LlmProvider {
	private static final String OLLAMA_BASE_URL = "http://localhost:11434";

	public static void main(String[] args) {
		LlmProvider provider = new LlmProvider();
		ChatLanguageModel model = provider.getModelForPurpose(Purpose.CHAT);
		System.out.println(model.chat("List major flowers in the language of flowers and give some example messages."));
	}

	private String getModelFromPurpose(Purpose purpose) {
		return switch (purpose) {
			case CHAT, JSON -> "llama3.2";
		};
	}

	private Set<Capability> getCapabilitiesFromPurpose(Purpose purpose) {
		return switch (purpose) {
			case CHAT -> Set.of();
			case JSON -> Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
		};
	}

	public ChatLanguageModel getModelForPurpose(Purpose purpose) {
		return OllamaChatModel.builder()
				.baseUrl(OLLAMA_BASE_URL)
				.modelName(getModelFromPurpose(purpose))
				.supportedCapabilities(getCapabilitiesFromPurpose(purpose))
				.build();
	}

	public StreamingChatLanguageModel getStreamingModelForPurpose(Purpose purpose) {
		return OllamaStreamingChatModel.builder()
				.baseUrl(OLLAMA_BASE_URL)
				.modelName(getModelFromPurpose(purpose))
				.supportedCapabilities(getCapabilitiesFromPurpose(purpose))
				.build();
	}

	public enum Purpose {
		CHAT,
		JSON,
	}
}
