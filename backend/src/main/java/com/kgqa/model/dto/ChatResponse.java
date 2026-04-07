package com.kgqa.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatResponse {
    private String answer;
    private List<String> sources;
    private String sessionId;

    public ChatResponse() {}

    public ChatResponse(String answer, List<String> sources, String sessionId) {
        this.answer = answer;
        this.sources = sources;
        this.sessionId = sessionId;
    }
}
