package id.enterpriseapps.assistant.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    private String systemPrompt;
    private List<ChatMessage> messages;
}
