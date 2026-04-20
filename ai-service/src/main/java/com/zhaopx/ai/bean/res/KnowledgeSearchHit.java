package com.zhaopx.ai.bean.res;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class KnowledgeSearchHit {

    private String id;

    private Double score;

    private Integer rank;

    private String content;

    private String systemCode;

    private String knowledgeBaseId;

    private String documentName;

    private Integer chunkIndex;

    private Map<String, Object> metadata;
}
