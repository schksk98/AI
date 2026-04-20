package com.zhaopx.ai.service.impl;

import com.zhaopx.ai.bean.req.KnowledgeAnswerRequest;
import com.zhaopx.ai.bean.req.KnowledgeSearchRequest;
import com.zhaopx.ai.bean.res.KnowledgeSearchHit;
import com.zhaopx.ai.bean.res.KnowledgeSearchResponse;
import com.zhaopx.ai.service.AnswerService;
import com.zhaopx.ai.service.KnowledgeSearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Service
public class AnswerServiceImpl implements AnswerService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private KnowledgeSearchService knowledgeSearchService;

    @Value("${answer.top-k:5}")
    private int topK;

    @Override
    public Flux<String> myQuestion(String question) {
        if (StringUtils.isBlank(question)) {
            return Flux.error(new IllegalArgumentException("question 不能为空"));
        }
        KnowledgeAnswerRequest request = new KnowledgeAnswerRequest();
        request.setQuestion(question.trim());
        request.setTopK(topK);
        return doAsk(request, false);
    }

    @Override
    public Flux<String> ask(KnowledgeAnswerRequest request) {
        if (request == null || StringUtils.isBlank(request.getQuestion())) {
            return Flux.error(new IllegalArgumentException("question 不能为空"));
        }
        if (request.getSystemCodes() == null || request.getSystemCodes().isEmpty()) {
            return Flux.error(new IllegalArgumentException("systemCodes 不能为空"));
        }
        return doAsk(request, true);
    }

    private Flux<String> doAsk(KnowledgeAnswerRequest request, boolean enforcePermission) {
        int effectiveTopK = Math.max(1, request.getTopK() == null ? topK : request.getTopK());

        return Mono.fromCallable(() -> {
                    KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
                    searchRequest.setQuery(request.getQuestion().trim());
                    searchRequest.setTopK(effectiveTopK);
                    searchRequest.setNumCandidates(request.getNumCandidates());
                    searchRequest.setKnowledgeBaseId(request.getKnowledgeBaseId());
                    if (enforcePermission) {
                        searchRequest.setSystemCodes(request.getSystemCodes());
                    } else {
                        searchRequest.setSystemCodes(List.of("default"));
                    }
                    return knowledgeSearchService.search(searchRequest);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(searchResponse -> {
                    String prompt = buildPrompt(request.getQuestion(), searchResponse);
                    log.info("RAG prompt length={}", prompt.length());
                    return chatClient.prompt()
                            .user(prompt)
                            .stream()
                            .content();
                })
                .doOnError(e -> {
                    if (e instanceof WebClientResponseException wcre) {
                        log.error("Dashscope chat 调用失败: status={}, body={}", wcre.getStatusCode(),
                                wcre.getResponseBodyAsString());
                    } else {
                        log.error("Dashscope chat 调用失败", e);
                    }
                })
                .onErrorResume(e -> Flux.just("模型生成失败（可能是 Dashscope 403/权限问题/模型不支持）："
                        + (e.getMessage() == null ? "" : e.getMessage())));
    }

    private String buildPrompt(String question, KnowledgeSearchResponse searchResponse) {
        StringBuilder context = new StringBuilder();
        List<KnowledgeSearchHit> hits = searchResponse == null ? null : searchResponse.getHits();
        if (hits != null) {
            for (int i = 0; i < hits.size(); i++) {
                KnowledgeSearchHit hit = hits.get(i);
                if (hit == null || StringUtils.isBlank(hit.getContent())) {
                    continue;
                }
                String text = hit.getContent().trim();
                if (text.length() > 2000) {
                    text = text.substring(0, 2000);
                }
                context.append("[参考资料 ").append(i + 1).append("]\n")
                        .append("systemCode=").append(StringUtils.defaultString(hit.getSystemCode(), "unknown"))
                        .append(", documentName=").append(StringUtils.defaultString(hit.getDocumentName(), "unknown"))
                        .append(", score=").append(hit.getScore() == null ? "null" : hit.getScore())
                        .append("\n")
                        .append(text)
                        .append("\n\n");
                if (context.length() >= 8000) {
                    break;
                }
            }
        }

        if (context.length() == 0) {
            context.append("（未检索到相关参考资料）");
        }

        return """
                你是一名专业的知识问答助手。请基于【参考资料】回答用户问题。
                要求：
                1. 只使用参考资料中的信息回答；如果参考资料不足以确定答案，请明确说明“资料不足，无法从中确定”。
                2. 用中文回答，尽量简洁清晰。

                【参考资料】
                %s

                【用户问题】
                %s

                【回答】
                """.formatted(context.toString(), question);
    }
}
