package com.kgqa.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;
    private String content;
    private Integer chunkIndex;
    private Long embeddingId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
