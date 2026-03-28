package com.agent.agentdemo.pipeline.handler;

import com.agent.agentdemo.pipeline.BaseQueryHandler;
import com.agent.agentdemo.pipeline.QueryContext;
import com.agent.agentdemo.pipeline.QueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 责任链第三节点（终端节点）：生成回答。
 *
 * 将检索结果拼装为 RAG 上下文，结合 {@link QueryIntent#summary()} 构建精准的 system prompt，
 * 以用户原始问题作为 user message，调用 LLM 流式生成回答，写入 {@link QueryContext#setResponseStream}。
 */
@Component
public class ResponseHandler extends BaseQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(ResponseHandler.class);

    private final ChatClient chatClient;

    public ResponseHandler(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public void handle(QueryContext context) {
        List<Document> docs = context.getRetrievedDocs();

        if (docs == null || docs.isEmpty()) {
            log.debug("ResponseHandler: no docs retrieved, returning empty hint");
            context.setResponseStream(
                    Flux.just("在知识库「" + context.getLibraryName() + "」中未找到与该问题相关的内容。")
            );
            return;
        }

        String ragContext = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = buildSystemPrompt(context.getIntent(), ragContext);

        log.debug("ResponseHandler: generating response with {} docs", docs.size());
        Flux<String> stream = chatClient.prompt()
                .system(systemPrompt)
                .user(context.getOriginalQuestion())
                .stream()
                .content();

        context.setResponseStream(stream);
        // 终端节点，不调用 passToNext
    }

    private String buildSystemPrompt(QueryIntent intent, String ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的文档问答助手。请严格根据以下参考文档内容回答用户的问题。\n\n");

        // 若意图解析成功，将意图摘要注入 prompt，引导模型聚焦
        if (intent != null) {
            sb.append("用户意图：").append(intent.summary()).append("\n");
            sb.append("意图类型：").append(translateIntentType(intent.intentType())).append("\n\n");
        }

        sb.append("""
                回答要求：
                1. 严格基于文档内容作答，不编造文档中没有的信息
                2. 如果文档中没有相关信息，请直接告知用户
                3. 回答清晰、准确，对 procedural 类问题使用步骤列表，对 conceptual 类问题先给出定义再展开

                参考文档：
                """);
        sb.append(ragContext);

        return sb.toString();
    }

    private String translateIntentType(String intentType) {
        if (intentType == null) return "未知";
        return switch (intentType.toLowerCase()) {
            case "factual"     -> "事实查询";
            case "procedural"  -> "操作步骤";
            case "conceptual"  -> "概念解释";
            default            -> "其他";
        };
    }
}
