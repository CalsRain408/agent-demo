package com.agent.agentdemo.config.pipeline;

import lombok.Data;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.util.StringUtils;

/**
 * 单个 LLM Handler 的配置，对应 YAML 中 agent.pipeline.{handler-name} 节点。
 */
@Data
public class HandlerConfig {

    /** 覆盖全局 model，若不填则沿用 spring.ai.openai.chat.options.model */
    private String model;

    /** 采样温度，值域 [0.0, 2.0]，越低越确定性 */
    private Double temperature;

    /** 最大输出 token 数，若不填则使用模型默认值 */
    private Integer maxTokens;

    /**
     * System prompt 模板。
     * ResponseHandler 支持以下占位符，其余 Handler 使用纯静态文本：
     *   {intent_summary}  — QueryIntent.summary()
     *   {intent_type}     — 翻译后的意图类型
     *   {rag_context}     — 检索到的文档拼接文本
     */
    private String systemPrompt;

    /**
     * 将本配置转换为 OpenAiChatOptions，仅覆盖显式配置的字段。
     * 未设置的字段将继承 ChatClient 的全局默认值。
     */
    public OpenAiChatOptions toChatOptions() {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        if (StringUtils.hasText(model))  builder.model(model);
        if (temperature != null)         builder.temperature(temperature);
        if (maxTokens   != null)         builder.maxTokens(maxTokens);
        return builder.build();
    }
}
