package ru.blackfan;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ai.chat.PromptException;
import burp.api.montoya.ai.chat.PromptResponse;
import burp.api.montoya.core.ByteArray;

public class AiSubstitutor {

    private final MontoyaApi api;
    private final PromptMessage promptMessageHandler;

    public AiSubstitutor(MontoyaApi api, PromptMessage promptMessageHandler) {
        this.api = api;
        this.promptMessageHandler = promptMessageHandler;
    }

    public ByteArray substitute(ByteArray selection, ByteArray before, ByteArray after) throws PromptException {
        PromptResponse promptResponse = api.ai().prompt().execute(promptMessageHandler.build(selection.toString()));
        return before.withAppended(promptResponse.content().getBytes()).withAppended(after);
    }
}
