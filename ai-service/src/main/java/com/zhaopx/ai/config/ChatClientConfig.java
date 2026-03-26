package com.zhaopx.ai.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 为兼容不同的 Spring AI 自动配置结果，显式创建 {@link ChatClient} Bean。
 *
 * 在某些版本中可能只暴露 ChatModel，而不是 ChatClient，本配置用于从 ChatModel 构造 ChatClient。
 */
@Configuration
public class ChatClientConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    public static final String MODEL = "kimi-k2.5";

    @Primary
    @Bean
    ChatModel AliCloudChatModel() {
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder()
                        .apiKey(apiKey)
                        .baseUrl("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation")
                        .build())
                .defaultOptions(DashScopeChatOptions.builder().model(MODEL).build())
                .build();
    }
    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultOptions(ChatOptions.builder().model(MODEL).build()).build();
    }
}

