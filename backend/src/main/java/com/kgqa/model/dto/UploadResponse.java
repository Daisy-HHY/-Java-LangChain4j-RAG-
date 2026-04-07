package com.kgqa.model.dto;

import lombok.Data;

@Data
public class UploadResponse {
    private Long knowledgeId;
    private String status;
    private String message;

    public UploadResponse() {}

    public UploadResponse(Long knowledgeId, String status, String message) {
        this.knowledgeId = knowledgeId;
        this.status = status;
        this.message = message;
    }
}
