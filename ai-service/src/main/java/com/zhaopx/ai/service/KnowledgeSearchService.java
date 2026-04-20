package com.zhaopx.ai.service;

import com.zhaopx.ai.bean.req.KnowledgeSearchRequest;
import com.zhaopx.ai.bean.res.KnowledgeSearchResponse;

public interface KnowledgeSearchService {

    KnowledgeSearchResponse search(KnowledgeSearchRequest request);
}
