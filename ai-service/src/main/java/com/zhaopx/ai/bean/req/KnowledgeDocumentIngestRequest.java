package com.zhaopx.ai.bean.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class KnowledgeDocumentIngestRequest {

    private String path;

    private String content;

    @NotBlank(message = "systemCode 不能为空")
    private String systemCode;

    private String knowledgeBaseId;

    private String bizId;

    private String documentName;

    private String sourceType;

    private List<String> tags;

    private Map<String, Object> customMetadata;

    private Integer chunkSize;
}
