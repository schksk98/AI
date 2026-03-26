package com.zhaopx.ai.service.impl;

import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import com.zhaopx.ai.service.ChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Description
 * @Author: ZhaoPengXiang
 * @Date: 2026-03-01 12:14
 */
@Slf4j
@Service
public class ChunkServiceImpl implements ChunkService {

    @Override
    public void doChunk(String text) {
        log.info("doChunk:{}", text);
        Document document = new Document(text);
        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(500);

        List<Document> chunks = splitter.apply(List.of(document));

        chunks.forEach(chunk -> {
            System.out.println(chunk.getText());
            System.out.println("--------");
        });
    }

}
