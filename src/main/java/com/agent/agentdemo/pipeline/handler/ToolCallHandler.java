package com.agent.agentdemo.pipeline.handler;

import com.agent.agentdemo.config.pipeline.PipelineConfigProvider;
import com.agent.agentdemo.config.pipeline.RetrievalConfig;
import com.agent.agentdemo.pipeline.BaseQueryHandler;
import com.agent.agentdemo.pipeline.QueryContext;
import com.agent.agentdemo.pipeline.QueryIntent;
import com.agent.agentdemo.mapper.DocumentMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 责任链第二节点：三阶段文档检索。
 *
 * <pre>
 * Stage 1 (粗筛) — documents 表
 *   根据 library_name 和 LLM 推断的 suggestedFileTypes 过滤文档，
 *   得到候选 document_id 集合，大幅缩小后续向量检索的搜索空间。
 *
 * Stage 2 (精筛) — 构建 FilterExpression
 *   在 Stage 1 结果之上叠加 chunk 级元数据条件：
 *     - document_id IN (Stage 1 结果)          始终生效
 *     - class_name == targetClassName          仅当意图中明确指定类名时
 *     - function_name == targetFunctionName    仅当意图中明确指定方法名时
 *
 * Stage 3 (向量检索) — cosine 相似度
 *   将精炼查询词（refinedQuery）通过 Embedding 接口转换为向量，
 *   在 Stage 2 过滤后的 chunk 集合内执行 cosine 相似度排序，取 TopK 结果。
 * </pre>
 *
 * <p>topK 和 similarityThreshold 通过 {@link PipelineConfigProvider} 注入，
 * 修改 {@code agent.pipeline.retrieval} 节点即可调整，无需改动代码。
 */
@Component
@Slf4j
public class ToolCallHandler extends BaseQueryHandler {
    @Resource
    private VectorStore vectorStore;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private PipelineConfigProvider configProvider;


    @Override
    public void handle(QueryContext context) {
        RetrievalConfig cfg    = configProvider.getRetrievalConfig();
        QueryIntent     intent = context.getIntent();

        // ── Stage 1: 粗筛 ─────────────────────────────────────────────────
        List<String> docIds = coarseFilter(context.getLibraryId(), intent);
        log.debug("ToolCallHandler Stage1: libraryId='{}' fileTypes={} → {} docs",
                context.getLibraryId(),
                intent != null ? intent.suggestedFileTypes() : "[]",
                docIds.size());

        if (docIds.isEmpty()) {
            log.debug("ToolCallHandler: no documents found after coarse filter");
            context.setRetrievedDocs(List.of());
            passToNext(context);
            return;
        }

        // ── Stage 2: 精筛 ─────────────────────────────────────────────────
        Filter.Expression filterExpr = buildChunkFilter(intent, docIds);
        log.debug("ToolCallHandler Stage2: filter={}", filterExpr);

        // ── Stage 3: 向量检索（topK / similarityThreshold 来自配置）────────
        String searchQuery = (intent != null && StringUtils.hasText(intent.refinedQuery()))
                ? intent.refinedQuery()
                : context.getOriginalQuestion();

        log.debug("ToolCallHandler Stage3: topK={} threshold={} query='{}'",
                cfg.getTopK(), cfg.getSimilarityThreshold(), searchQuery);

        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(searchQuery)
                .topK(cfg.getTopK())
                .filterExpression(filterExpr);

        if (cfg.getSimilarityThreshold() > 0.0) {
            requestBuilder.similarityThreshold(cfg.getSimilarityThreshold());
        }

        List<Document> docs = vectorStore.similaritySearch(requestBuilder.build());

        log.debug("ToolCallHandler: retrieved {} chunks", docs.size());
        context.setRetrievedDocs(docs);
        passToNext(context);
    }

    // ── Stage 1：documents 表粗筛 ─────────────────────────────────────────

    private List<String> coarseFilter(String libraryId, QueryIntent intent) {
        List<String> fileTypes = (intent != null && intent.suggestedFileTypes() != null)
                ? intent.suggestedFileTypes()
                : List.of();

        if (fileTypes.isEmpty()) {
            return documentMapper.selectIdsByLibraryId(libraryId);
        }
        return documentMapper.selectIdsByLibraryIdAndFileTypes(libraryId, fileTypes);
    }

    // ── Stage 2：构建 chunk 级 FilterExpression ───────────────────────────

    private Filter.Expression buildChunkFilter(QueryIntent intent, List<String> docIds) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();

        List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();
        conditions.add(b.in("document_id", (Object[]) docIds.toArray(new String[0])));

        if (intent != null) {
            if (StringUtils.hasText(intent.targetClassName())) {
                conditions.add(b.eq("class_name", intent.targetClassName()));
                log.debug("ToolCallHandler Stage2: + class_name='{}'", intent.targetClassName());
            }
            if (StringUtils.hasText(intent.targetFunctionName())) {
                conditions.add(b.eq("function_name", intent.targetFunctionName()));
                log.debug("ToolCallHandler Stage2: + function_name='{}'", intent.targetFunctionName());
            }
        }

        return conditions.stream().reduce(b::and).orElseThrow().build();
    }
}
