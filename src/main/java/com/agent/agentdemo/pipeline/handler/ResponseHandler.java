package com.agent.agentdemo.pipeline.handler;

import com.agent.agentdemo.config.pipeline.HandlerConfig;
import com.agent.agentdemo.config.pipeline.PipelineConfigProvider;
import com.agent.agentdemo.entity.LibraryEntity;
import com.agent.agentdemo.mapper.LibraryMapper;
import com.agent.agentdemo.pipeline.BaseQueryHandler;
import com.agent.agentdemo.pipeline.QueryContext;
import com.agent.agentdemo.pipeline.QueryIntent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;


@Component
@Slf4j
public class ResponseHandler extends BaseQueryHandler {

    @Resource
    private ChatClient chatClient;

    @Resource
    private PipelineConfigProvider configProvider;

    @Autowired
    private LibraryMapper libraryMapper;


    /**
     * 责任链第三节点（终端节点）：生成回答。
     * 将检索结果拼装为 RAG 上下文，并通过模板替换将动态内容注入 system prompt：
     * {@code {intent_summary}} — QueryIntent.summary()
     * {@code {intent_type}}    — 翻译后的意图类型
     * {@code {rag_context}}    — 检索到的文档 chunk 拼接文本
     * 模板内容、model、temperature 均通过 {@link PipelineConfigProvider} 注入，
     * 修改 {@code agent.pipeline.response} 节点即可调整，无需改动代码。
     */
    @Override
    public void handle(QueryContext context) {
        List<Document> docs = context.getRetrievedDocs();

        if (docs == null || docs.isEmpty()) {
            log.debug("ResponseHandler: no docs retrieved, returning empty hint");

            LibraryEntity library = libraryMapper.selectById(context.getLibraryId());
            String libraryName = (library != null) ? library.getName() : context.getLibraryId();

            context.setResponseStream(
                    Flux.just("在知识库「" + libraryName + "」中未找到与该问题相关的内容。")
            );
            return;
        }

        HandlerConfig cfg = configProvider.getResponseConfig();

        String ragContext = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = renderPrompt(cfg.getSystemPrompt(), context.getIntent(), ragContext);

        log.debug("ResponseHandler: model={} temp={} docs={}", cfg.getModel(), cfg.getTemperature(), docs.size());
        Flux<String> stream = chatClient.prompt()
                .options(cfg.toChatOptions())
                .system(systemPrompt)
                .user(context.getOriginalQuestion())
                .stream()
                .content();

        context.setResponseStream(stream);
        // 终端节点，不调用 passToNext
    }

    /**
     * 将配置中的 system prompt 模板与运行时变量合并。
     * 占位符约定（与 YAML 模板中的 {key} 对应）：
     *   {intent_summary}、{intent_type}、{rag_context}
     */
    private String renderPrompt(String template, QueryIntent intent, String ragContext) {
        String intentSummary = (intent != null && StringUtils.hasText(intent.summary()))
                ? intent.summary() : "（未解析）";
        String intentType    = (intent != null)
                ? translateIntentType(intent.intentType()) : "（未解析）";

        return template
                .replace("{intent_summary}", intentSummary)
                .replace("{intent_type}",    intentType)
                .replace("{rag_context}",    ragContext);
    }

    private String translateIntentType(String intentType) {
        if (intentType == null) return "其他";
        return switch (intentType.toLowerCase()) {
            case "factual"    -> "事实查询";
            case "procedural" -> "操作步骤";
            case "conceptual" -> "概念解释";
            default           -> "其他";
        };
    }
}
