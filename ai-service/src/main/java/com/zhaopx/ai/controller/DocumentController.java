package com.zhaopx.ai.controller;

import com.zhaopx.ai.bean.req.KnowledgeDocumentIngestRequest;
import com.zhaopx.ai.bean.res.KnowledgeDocumentIngestResponse;
import com.zhaopx.ai.bean.res.R;
import com.zhaopx.ai.service.ChunkService;
import com.zhaopx.ai.service.DocumentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;

/**
 * @Description
 * @Author: ZhaoPengXiang
 * @Date: 2026-03-13 11:38
 */
@Slf4j
@RestController
@RequestMapping("/chunk")
public class DocumentController {

    @Autowired
    private ChunkService chunkService;
    @Autowired
    private DocumentService documentService;


    @PostMapping("/doChunk")
    public R<Object> doChunk(@RequestBody String text) {
        chunkService.doChunk(text);
        return R.success();
    }

    @PostMapping("/ingest")
    public R<KnowledgeDocumentIngestResponse> ingest(@Valid @RequestBody KnowledgeDocumentIngestRequest request) {
        try {
            return R.success(documentService.ingestDocument(request));
        } catch (Exception e) {
            log.error("知识库文档入库失败: {}", e.getMessage(), e);
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/doReadDocument")
    public R<Object> doReadDocument(@RequestParam("path") String path,
                                    @RequestParam(value = "systemCode", required = false, defaultValue = "default") String systemCode,
                                    @RequestParam(value = "knowledgeBaseId", required = false) String knowledgeBaseId,
                                    @RequestParam(value = "documentName", required = false) String documentName) throws Exception {
        try {
            KnowledgeDocumentIngestRequest request = new KnowledgeDocumentIngestRequest();
            request.setPath(path);
            request.setSystemCode(systemCode);
            request.setKnowledgeBaseId(knowledgeBaseId);
            request.setDocumentName(documentName);
            return R.success(documentService.ingestDocument(request));
        } catch (FileNotFoundException fe) {
            log.error("文件：{}，不存在", path);
            return R.fail(path + "文件不存在");
        } catch (Exception e) {
            log.error("系统异常：{}", e.getMessage(), e);
            return R.fail(e.getMessage());
        }
    }
}
