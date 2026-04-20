package com.zhaopx.ai.service.impl;

import com.alibaba.cloud.ai.reader.poi.PoiDocumentReader;
import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import com.zhaopx.ai.bean.req.KnowledgeDocumentIngestRequest;
import com.zhaopx.ai.bean.res.KnowledgeDocumentIngestResponse;
import com.zhaopx.ai.service.DocumentService;
import com.zhaopx.ai.support.KnowledgeMetadataConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    @Value("${document.embedding.batch-size:10}")
    private int embeddingBatchSize;

    @Value("${document.chunk.size:500}")
    private int defaultChunkSize;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name}")
    private String indexName;

    @Autowired
    private VectorStore vectorStore;

    @Override
    public KnowledgeDocumentIngestResponse ingestDocument(KnowledgeDocumentIngestRequest request) {
        validateRequest(request);

        String documentId = UUID.randomUUID().toString();
        String normalizedPath = normalizePath(request.getPath());
        String sourceType = StringUtils.defaultIfBlank(request.getSourceType(),
                StringUtils.isNotBlank(normalizedPath) ? "file" : "manual");
        String documentName = resolveDocumentName(request.getDocumentName(), normalizedPath, documentId);

        log.info("开始处理知识库文档入库，systemCode={}, knowledgeBaseId={}, documentName={}, sourceType={}",
                request.getSystemCode(), request.getKnowledgeBaseId(), documentName, sourceType);

        List<Document> sourceDocuments = loadDocuments(request, normalizedPath);
        List<Document> chunkDocuments = splitDocuments(sourceDocuments, request, documentId, documentName, normalizedPath, sourceType);
        int batchCount = writeToVectorStore(chunkDocuments);

        log.info("知识库文档入库完成，documentId={}, chunkCount={}, batchCount={}", documentId, chunkDocuments.size(), batchCount);

        return KnowledgeDocumentIngestResponse.builder()
                .documentId(documentId)
                .systemCode(request.getSystemCode().trim())
                .knowledgeBaseId(StringUtils.trimToNull(request.getKnowledgeBaseId()))
                .documentName(documentName)
                .chunkCount(chunkDocuments.size())
                .batchCount(batchCount)
                .indexName(indexName)
                .build();
    }

    @Override
    public void doReadDocument(String path) {
        KnowledgeDocumentIngestRequest request = new KnowledgeDocumentIngestRequest();
        request.setPath(path);
        request.setSystemCode("default");
        ingestDocument(request);
    }

    private void validateRequest(KnowledgeDocumentIngestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("入库请求不能为空");
        }
        if (StringUtils.isBlank(request.getSystemCode())) {
            throw new IllegalArgumentException("systemCode 不能为空");
        }
        if (StringUtils.isBlank(request.getPath()) && StringUtils.isBlank(request.getContent())) {
            throw new IllegalArgumentException("path 和 content 至少需要一个");
        }
    }

    private List<Document> loadDocuments(KnowledgeDocumentIngestRequest request, String normalizedPath) {
        if (StringUtils.isNotBlank(request.getContent())) {
            return List.of(new Document(request.getContent().trim()));
        }

        String location = buildDocumentLocation(normalizedPath);
        PoiDocumentReader reader = new PoiDocumentReader(location);
        return reader.get();
    }

    private List<Document> splitDocuments(List<Document> sourceDocuments,
                                          KnowledgeDocumentIngestRequest request,
                                          String documentId,
                                          String documentName,
                                          String normalizedPath,
                                          String sourceType) {
        int chunkSize = request.getChunkSize() == null ? defaultChunkSize : Math.max(100, request.getChunkSize());
        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(chunkSize);
        List<Document> chunks = splitter.apply(sourceDocuments);
        if (chunks == null || chunks.isEmpty()) {
            log.info("文档分段结果为空，跳过向量入库");
            return List.of();
        }

        List<Document> enrichedChunks = new ArrayList<>(chunks.size());
        int totalChunks = chunks.size();
        for (int i = 0; i < totalChunks; i++) {
            Document chunk = chunks.get(i);
            if (chunk == null || StringUtils.isBlank(chunk.getText())) {
                continue;
            }

            Map<String, Object> metadata = buildMetadata(request, documentId, documentName, normalizedPath, sourceType, i, totalChunks);
            if (chunk.getMetadata() != null && !chunk.getMetadata().isEmpty()) {
                metadata.putAll(chunk.getMetadata());
            }
            metadata.put(KnowledgeMetadataConstants.SYSTEM_CODE, request.getSystemCode().trim());
            metadata.put(KnowledgeMetadataConstants.KNOWLEDGE_BASE_ID, StringUtils.trimToNull(request.getKnowledgeBaseId()));
            metadata.put(KnowledgeMetadataConstants.DOCUMENT_ID, documentId);
            metadata.put(KnowledgeMetadataConstants.DOCUMENT_NAME, documentName);
            metadata.put(KnowledgeMetadataConstants.CHUNK_INDEX, i);
            metadata.put(KnowledgeMetadataConstants.CHUNK_COUNT, totalChunks);

            enrichedChunks.add(new Document(documentId + "-" + i, chunk.getText(), metadata));
        }
        return enrichedChunks;
    }

    private Map<String, Object> buildMetadata(KnowledgeDocumentIngestRequest request,
                                              String documentId,
                                              String documentName,
                                              String normalizedPath,
                                              String sourceType,
                                              int chunkIndex,
                                              int totalChunks) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(KnowledgeMetadataConstants.SYSTEM_CODE, request.getSystemCode().trim());
        metadata.put(KnowledgeMetadataConstants.KNOWLEDGE_BASE_ID, StringUtils.trimToNull(request.getKnowledgeBaseId()));
        metadata.put(KnowledgeMetadataConstants.BIZ_ID, StringUtils.trimToNull(request.getBizId()));
        metadata.put(KnowledgeMetadataConstants.DOCUMENT_ID, documentId);
        metadata.put(KnowledgeMetadataConstants.DOCUMENT_NAME, documentName);
        metadata.put(KnowledgeMetadataConstants.DOCUMENT_PATH, StringUtils.trimToNull(normalizedPath));
        metadata.put(KnowledgeMetadataConstants.SOURCE_TYPE, sourceType);
        metadata.put(KnowledgeMetadataConstants.TAGS, request.getTags());
        metadata.put(KnowledgeMetadataConstants.CHUNK_INDEX, chunkIndex);
        metadata.put(KnowledgeMetadataConstants.CHUNK_COUNT, totalChunks);
        metadata.put(KnowledgeMetadataConstants.FILE_EXTENSION, resolveFileExtension(normalizedPath, documentName));
        metadata.put(KnowledgeMetadataConstants.INGESTED_AT, OffsetDateTime.now().toString());
        if (request.getCustomMetadata() != null && !request.getCustomMetadata().isEmpty()) {
            metadata.putAll(request.getCustomMetadata());
        }
        return metadata;
    }

    private int writeToVectorStore(List<Document> documentsList) {
        if (documentsList == null || documentsList.isEmpty()) {
            return 0;
        }

        int batchSize = Math.max(1, Math.min(embeddingBatchSize, 10));
        int total = documentsList.size();
        int batchCount = (total + batchSize - 1) / batchSize;
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int from = batchIndex * batchSize;
            int to = Math.min(from + batchSize, total);
            List<Document> batch = documentsList.subList(from, to);
            log.info("开始向量入库批次 {}/{} (size={}, batchSize={})", batchIndex + 1, batchCount, batch.size(), batchSize);
            vectorStore.add(batch);
        }
        return batchCount;
    }

    private String buildDocumentLocation(String normalizedPath) {
        if (StringUtils.isBlank(normalizedPath)) {
            throw new IllegalArgumentException("path 不能为空");
        }

        String location = normalizedPath;
        if (!normalizedPath.startsWith("classpath:") && !normalizedPath.startsWith("file:")) {
            Path path = Paths.get(normalizedPath);
            if (path.isAbsolute()) {
                if (!Files.exists(path)) {
                    throw new IllegalArgumentException("文件不存在: " + path);
                }
                location = path.toUri().toString();
            }
        }
        return location;
    }

    private String normalizePath(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }

        String normalized = path.trim();
        if (normalized.startsWith("Users/")) {
            normalized = "/" + normalized;
        }
        if (normalized.startsWith("~/")) {
            normalized = System.getProperty("user.home") + normalized.substring(1);
        }
        return normalized;
    }

    private String resolveDocumentName(String requestDocumentName, String normalizedPath, String documentId) {
        if (StringUtils.isNotBlank(requestDocumentName)) {
            return requestDocumentName.trim();
        }
        if (StringUtils.isBlank(normalizedPath)) {
            return "doc-" + documentId;
        }
        Path path = Paths.get(normalizedPath);
        Path fileName = path.getFileName();
        return fileName == null ? "doc-" + documentId : fileName.toString();
    }

    private String resolveFileExtension(String normalizedPath, String documentName) {
        String target = StringUtils.defaultIfBlank(documentName, normalizedPath);
        if (StringUtils.isBlank(target) || !target.contains(".")) {
            return null;
        }
        return StringUtils.substringAfterLast(target, ".").toLowerCase();
    }
}
