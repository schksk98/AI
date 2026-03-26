package com.zhaopx.ai.service.impl;

import com.alibaba.cloud.ai.reader.poi.PoiDocumentReader;
import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import com.zhaopx.ai.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * @Description
 * @Author: ZhaoPengXiang
 * @Date: 2026-03-17 10:08
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {
    /**
     * Dashscope embedding 接口限制：batch size 不能超过 10。
     * 允许通过配置覆盖：document.embedding.batch-size
     */
    @Value("${document.embedding.batch-size:10}")
    private int embeddingBatchSize;

    @Autowired
    private VectorStore vectorStore;

    @Override
    public void doReadDocument(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }

        // 某些情况下（例如请求参数未编码/被裁剪）可能丢失开头的 '/'
        String normalized = path.trim();
        if (normalized.startsWith("Users/")) {
            normalized = "/" + normalized;
        }

        // 支持 "~/" 形式
        if (normalized.startsWith("~/")) {
            normalized = System.getProperty("user.home") + normalized.substring(1);
        }

        log.info("开始处理文档：{}", normalized);

        String location = normalized;
        if (!normalized.startsWith("classpath:") && !normalized.startsWith("file:")) {
            Path p = Paths.get(normalized);
            if (p.isAbsolute()) {
                if (!Files.exists(p)) {
                    throw new IllegalArgumentException("文件不存在: " + p);
                }
                location = p.toUri().toString(); // file:///...
            }
        }

        // 1、读取文档
        PoiDocumentReader reader = new PoiDocumentReader(location);
        List<Document> documents = reader.get();

        // 2、处理文档，chunk分段
        RecursiveCharacterTextSplitter recursiveCharacterTextSplitter = new RecursiveCharacterTextSplitter(500);
        List<Document> documentsList = recursiveCharacterTextSplitter.apply(documents);

        if (documentsList == null || documentsList.isEmpty()) {
            log.info("文档分段结果为空，跳过向量入库");
            return;
        }
        // 3、存向量库（embedding 接口对一次入参 texts 数量有限制）
        int batchSize = Math.max(1, Math.min(embeddingBatchSize, 10));
        int total = documentsList.size();
        int batchCount = (total + batchSize - 1) / batchSize;
        // 分批次入库
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int from = batchIndex * batchSize;
            int to = Math.min(from + batchSize, total);
            List<Document> batch = documentsList.subList(from, to);
            log.info("开始向量入库批次 {}/{} (size={}, batchSize={})", batchIndex + 1, batchCount, batch.size(), batchSize);
            vectorStore.add(batch);
        }
        log.info("处理完成");
    }
}
