package com.zhaopx.ai.bean.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeSearchResponse {

    private String query;

    private Integer hitCount;

    private String searchType;

    private List<KnowledgeSearchHit> hits;
}
