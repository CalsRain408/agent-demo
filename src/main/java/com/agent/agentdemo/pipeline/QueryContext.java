package com.agent.agentdemo.pipeline;

import lombok.Builder;
import lombok.Data;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 贯穿整条责任链的上下文对象。
 * 各 Handler 从中读取输入、将结果写回，由下一个节点继续处理。
 */
@Data
@Builder
public class QueryContext {

    //入参（不可变）
    private final String libraryId;
    private final String originalQuestion;

    // AnalysisHandler 写入
    private QueryIntent intent;

    // ToolCallHandler 写入
    private List<Document> retrievedDocs;

    // ResponseHandler 写入
    private Flux<String> responseStream;

    public QueryContext(String libraryId, String originalQuestion) {
        this.libraryId      = libraryId;
        this.originalQuestion = originalQuestion;
    }
}
