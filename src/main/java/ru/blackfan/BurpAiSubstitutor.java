package ru.blackfan;

import burp.api.montoya.ai.Ai;
import burp.api.montoya.ai.chat.PromptException;
import burp.api.montoya.BurpExtension;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.EnhancedCapability;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import java.awt.Component;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.List;
import java.util.Set;
import javax.swing.JMenuItem;
import static burp.api.montoya.EnhancedCapability.AI_FEATURES;
import static burp.api.montoya.ui.contextmenu.InvocationType.MESSAGE_EDITOR_REQUEST;

public class BurpAiSubstitutor implements BurpExtension, ContextMenuItemsProvider {

    private Ai ai;
    private AiSubstitutor aiSubstitutor;
    private ExecutorService executorService;
    private Logging logging;
    private static final String SYSTEM_MESSAGE
            = """
Your job is to replace placeholder values in query parameters, http headers or request bodies with realistic example data.
You must preserve the structure and syntax of the request, but substitute all generic placeholders (string, number, value, etc.) with appropriate, contextually accurate sample values.
Respond only with the modified HTTP request. Do not provide any additional explanation or commentary. Do not use markdown syntax. Do not add new parameters.
If there are no placeholders to replace or any other error occurs, return the original text unchanged.
""";

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("AI Substitutor");
        ai = api.ai();
        PromptMessage promptMessageHandler = new PromptMessage(SYSTEM_MESSAGE);
        logging = api.logging();
        logging.logToOutput("AI Substitutor");
        logging.logToOutput("Version 1.0.0");
        logging.logToOutput("Created by Sergey Bobrov (BlackFan)");
        aiSubstitutor = new AiSubstitutor(api, promptMessageHandler);
        executorService = Executors.newFixedThreadPool(5);
        api.userInterface().registerContextMenuItemsProvider(this);
        api.extension().registerUnloadingHandler(executorService::shutdownNow);
    }

    @Override
    public Set<EnhancedCapability> enhancedCapabilities() {
        return Set.of(AI_FEATURES);
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        if (ai.isEnabled()) {
            if (event.isFrom(MESSAGE_EDITOR_REQUEST)) {
                if (event.messageEditorRequestResponse().isPresent()
                        && event.messageEditorRequestResponse().get().selectionOffsets().isPresent()) {

                    JMenuItem menuItem = new JMenuItem("Substitute");
                    menuItem.addActionListener(l -> {
                        var messageEditor = event.messageEditorRequestResponse().get();
                        var requestResponse = messageEditor.requestResponse();
                        var request = requestResponse.request();
                        Range selectionOffsets = messageEditor.selectionOffsets().get();

                        if (selectionOffsets != null) {
                            ByteArray originalBytes = request.toByteArray();
                            int length = originalBytes.length();
                            int start = selectionOffsets.startIndexInclusive();
                            int end = selectionOffsets.endIndexExclusive();

                            if (start < 0 || end > length || start > end) {
                                logging.logToError("Invalid selection range: start=" + start + ", end=" + end + ", length=" + length);
                                return;
                            }

                            ByteArray before = originalBytes.subArray(0, start);
                            ByteArray selection = originalBytes.subArray(start, end);
                            ByteArray after = (end == length) ? ByteArray.byteArray("") : originalBytes.subArray(end, length);

                            executorService.submit(() -> {
                                try {
                                    ByteArray newRequest = aiSubstitutor.substitute(selection, before, after);
                                    messageEditor.setRequest(HttpRequest.httpRequest(newRequest));
                                } catch (PromptException e) {
                                    if (e.getMessage().contains("Not enough credits")) {
                                        logging.logToOutput("Please increase your credit balance.");
                                    } else {
                                        logging.logToError("Issue executing prompt", e);
                                    }
                                }
                            });
                        }
                    });
                    return List.of(menuItem);
                }
            }
        } else {
            logging.logToOutput("AI is not enabled. Enable it in Burp.");
        }
        return List.of();
    }
}
