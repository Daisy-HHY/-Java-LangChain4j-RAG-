package com.kgqa.model.dto;

import lombok.Data;

@Data
public class KnowledgeDTO {
    private Long id;
    private String title;
    private String fileName;
    private String fileType;
    private String tags;
    private Integer chunkCount;
    private String status;
    private String createdAt;
}
