package com.zhaopx.ai.service.impl;

import com.zhaopx.ai.service.AnswerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * @Description
 * @Author: ZhaoPengXiang
 * @Date: 2026-03-26 14:38
 */
@Slf4j
@Service
public class AnswerServiceImpl implements AnswerService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private VectorStore vectorStore;

    // 向量检索返回前端/LLM 使用的命中文本条数
    @Value("${answer.top-k:5}")
    private int topK;

    @Override
    public Flux<String> myQuestion(String question) {
        if (question == null || question.isBlank()) {
            return Flux.error(new IllegalArgumentException("question 不能为空"));
        }

        int effectiveTopK = Math.max(1, topK);
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(effectiveTopK)
                .build();

        // similaritySearch 是阻塞式调用，放到 boundedElastic 线程池避免阻塞 Netty 线程
        return Mono.fromCallable(() -> vectorStore.similaritySearch(searchRequest))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(docs -> {
                    String prompt = buildPrompt(question, docs);
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

    private String buildPrompt(String question, java.util.List<Document> docs) {
        StringBuilder context = new StringBuilder();
        if (docs != null) {
            for (int i = 0; i < docs.size(); i++) {
                Document d = docs.get(i);
                if (d == null || d.getText() == null) {
                    continue;
                }
                String text = d.getText().trim();
                if (text.isEmpty()) {
                    continue;
                }
                // 避免单条 chunk 过长把上下文撑爆
                int maxPerDocChars = 2000;
                if (text.length() > maxPerDocChars) {
                    text = text.substring(0, maxPerDocChars);
                }
                context.append("[参考资料 ").append(i + 1).append("]\n")
                        .append(text).append("\n\n");
                // 控制总上下文长度（经验值，可按需调大/调小）
                int maxContextChars = 8000;
                if (context.length() >= maxContextChars) {
                    break;
                }
            }
        }

        // 若没召回到内容，也要给模型一个兜底指令
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
