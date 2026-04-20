package com.zhaopx.ai.bean.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeDocumentIngestResponse {

    private String documentId;

    private String systemCode;

    private String knowledgeBaseId;

    private String documentName;

    private Integer chunkCount;

    private Integer batchCount;

    private String indexName;
}
