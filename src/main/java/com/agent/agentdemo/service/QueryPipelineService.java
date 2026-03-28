package com.agent.agentdemo.service;

import com.agent.agentdemo.pipeline.QueryContext;
import com.agent.agentdemo.pipeline.QueryHandler;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 查询流水线入口。
 * 构造 {@link QueryContext} 并驱动责任链执行，最终返回流式响应。
 */
@Service
public class QueryPipelineService {

    private final QueryHandler queryChain;

    public QueryPipelineService(QueryHandler queryChain) {
        this.queryChain = queryChain;
    }

    public Flux<String> query(String libraryName, String question) {
        QueryContext context = new QueryContext(libraryName, question);
        queryChain.handle(context);
        return context.getResponseStream() != null
                ? context.getResponseStream()
                : Flux.just("查询处理失败，请稍后重试。");
    }
}
