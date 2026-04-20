package com.zhaopx.ai.service;

import com.zhaopx.ai.bean.req.KnowledgeAnswerRequest;
import reactor.core.publisher.Flux;

/**
 * @Description
 * @Author: ZhaoPengXiang
 * @Date: 2026-03-26 14:38
 */
public interface AnswerService {
    Flux<String> myQuestion(String question);

    Flux<String> ask(KnowledgeAnswerRequest request);
}
