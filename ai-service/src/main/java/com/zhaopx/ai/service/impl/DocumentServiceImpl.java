package com.zhaopx.ai.service.impl;

import com.alibaba.cloud.ai.reader.poi.PoiDocumentReader;
import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import com.zhaopx.ai.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Description
 * @Author: ZhaoPengXiang
 * @Date: 2026-03-17 10:08
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    // @Autowired
    // private VectorStore vectorStore;

    @Override
    public void doReadDocument(String path) {
        log.info("开始处理文档：{}", path);

        // 1、读取文档
        PoiDocumentReader reder = new PoiDocumentReader(path);
        List<Document> documents = reder.get();

        // 2、处理文档，chunk分段
        RecursiveCharacterTextSplitter recursiveCharacterTextSplitter = new RecursiveCharacterTextSplitter(500);
        List<Document> documentsList = recursiveCharacterTextSplitter.apply(documents);

        // 3、存向量库
        // vectorStore.add(documentsList);

        log.info("处理完成");
    }
}
