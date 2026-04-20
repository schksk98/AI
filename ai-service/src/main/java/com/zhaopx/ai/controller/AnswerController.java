package com.zhaopx.ai.controller;

import com.zhaopx.ai.bean.req.KnowledgeAnswerRequest;
import com.zhaopx.ai.bean.req.KnowledgeSearchRequest;
import com.zhaopx.ai.bean.res.KnowledgeSearchResponse;
import com.zhaopx.ai.bean.res.R;
import com.zhaopx.ai.service.AnswerService;
import com.zhaopx.ai.service.KnowledgeSearchService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @Description
 * @Author: ZhaoPengXiang
 * @Date: 2026-03-26 14:32
 */
@Slf4j
@RequestMapping("/answer")
@RestController
public class AnswerController {

    @Autowired
    private AnswerService answerService;

    @Autowired
    private KnowledgeSearchService knowledgeSearchService;

    @GetMapping(value = "/myQuestion", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> myQuestion(@RequestParam("question") String question) {
        return answerService.myQuestion(question);
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ask(@Valid @RequestBody KnowledgeAnswerRequest request) {
        return answerService.ask(request);
    }

    @PostMapping("/search")
    public R<KnowledgeSearchResponse> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        try {
            return R.success(knowledgeSearchService.search(request));
        } catch (Exception e) {
            log.error("知识库 Hybrid Search 失败: {}", e.getMessage(), e);
            return R.fail(e.getMessage());
        }
    }
}
