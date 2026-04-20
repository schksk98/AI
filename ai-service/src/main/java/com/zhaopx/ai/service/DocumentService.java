package com.zhaopx.ai.service;

import com.zhaopx.ai.bean.req.KnowledgeDocumentIngestRequest;
import com.zhaopx.ai.bean.res.KnowledgeDocumentIngestResponse;

/**
 * @Description
 * @Author: ZhaoPengXiang
 * @Date: 2026-03-17 10:08
 */
public interface DocumentService {
    KnowledgeDocumentIngestResponse ingestDocument(KnowledgeDocumentIngestRequest request) throws Exception;

    void doReadDocument(String path) throws Exception;
}
