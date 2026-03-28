package com.agent.agentdemo.pipeline.handler;

import com.agent.agentdemo.pipeline.BaseQueryHandler;
import com.agent.agentdemo.pipeline.QueryContext;
import com.agent.agentdemo.pipeline.QueryIntent;
import com.agent.agentdemo.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;


@Component
public class ToolCallHandler extends BaseQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(ToolCallHandler.class);
    private static final int TOP_K = 5;

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;

    public ToolCallHandler(VectorStore vectorStore, DocumentRepository documentRepository) {
        this.vectorStore        = vectorStore;
        this.documentRepository = documentRepository;
    }

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
     */
    @Override
    public void handle(QueryContext context) {
        QueryIntent intent = context.getIntent();

        //  1: 粗筛
        List<String> docIds = coarseFilter(context.getLibraryName(), intent);
        log.debug("ToolCallHandler Stage1: library='{}' fileTypes={} → {} docs",
                context.getLibraryName(),
                intent != null ? intent.suggestedFileTypes() : "[]",
                docIds.size());

        if (docIds.isEmpty()) {
            log.debug("ToolCallHandler: no documents found after coarse filter");
            context.setRetrievedDocs(List.of());
            passToNext(context);
            return;
        }

        //  2: 精筛
        Filter.Expression filterExpr = buildChunkFilter(intent, docIds);
        log.debug("ToolCallHandler Stage2: filter={}", filterExpr);

        //  3: 向量检索
        String searchQuery = (intent != null && StringUtils.hasText(intent.refinedQuery()))
                ? intent.refinedQuery()
                : context.getOriginalQuestion();

        log.debug("ToolCallHandler Stage3: searchQuery='{}'", searchQuery);
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(searchQuery)
                        .topK(TOP_K)
                        .filterExpression(filterExpr)
                        .build()
        );

        log.debug("ToolCallHandler: retrieved {} chunks", docs.size());
        context.setRetrievedDocs(docs);
        passToNext(context);
    }


    /**
     * 从 documents 表获取候选文档 ID。
     * 若 LLM 推断出 suggestedFileTypes，则在 SQL 层面叠加 file_type IN (…) 条件，
     * 否则返回该知识库下所有文档。
     */
    private List<String> coarseFilter(String libraryName, QueryIntent intent) {
        List<String> fileTypes = (intent != null && intent.suggestedFileTypes() != null)
                ? intent.suggestedFileTypes()
                : List.of();

        if (fileTypes.isEmpty()) {
            return documentRepository.findIdsByLibraryName(libraryName);
        }
        return documentRepository.findIdsByLibraryNameAndFileTypes(libraryName, fileTypes);
    }


    /**
     * 以 Stage 1 的 document_id 集合为基础，可选地追加代码符号级条件（class_name / function_name）。
     * 多个条件之间用 AND 连接，确保每一项都是对结果的进一步收窄。
     *
     * TODO: 若 class_name / function_name 条件导致结果为空，可在调用方加降级重试逻辑
     * 目前保持简单，由用户修改问题描述来获得更宽泛的结果。
     */
    private Filter.Expression buildChunkFilter(QueryIntent intent, List<String> docIds) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();

        // 基础条件：chunk 必须属于 Stage 1 筛出的文档
        List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();
        conditions.add(b.in("document_id", (Object[]) docIds.toArray(new String[0])));

        // 代码符号精筛（仅当 LLM 明确提取到类名或函数名时才加入）
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

        // 将所有条件用 AND 折叠
        return conditions.stream()
                .reduce(b::and)
                .orElseThrow()
                .build();
    }
}
