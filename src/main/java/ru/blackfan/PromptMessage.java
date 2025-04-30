package ru.blackfan;

import burp.api.montoya.ai.chat.Message;

import static burp.api.montoya.ai.chat.Message.systemMessage;
import static burp.api.montoya.ai.chat.Message.userMessage;

public class PromptMessage {

    private final Message systemMessage;

    public PromptMessage(String systemPrompt) {
        systemMessage = systemMessage(systemPrompt);
    }

    public Message[] build(String userPrompt) {
        return new Message[]{systemMessage, userMessage(userPrompt)};
    }
}
