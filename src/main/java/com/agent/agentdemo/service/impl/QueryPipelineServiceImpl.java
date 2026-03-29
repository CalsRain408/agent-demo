package com.agent.agentdemo.service.impl;

import com.agent.agentdemo.entity.LibraryEntity;
import com.agent.agentdemo.pipeline.QueryContext;
import com.agent.agentdemo.pipeline.QueryHandler;
import com.agent.agentdemo.service.LibraryService;
import com.agent.agentdemo.service.QueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 查询流水线入口。
 * 将 libraryName 解析为 libraryId，构造 {@link QueryContext} 并驱动责任链执行。
 */
@Service
public class QueryPipelineServiceImpl implements QueryService {

    @Autowired
    private QueryHandler queryChain;

    @Autowired
    private LibraryService knowledgeService;

    @Override
    public Flux<String> query(String libraryName, String question) {
        LibraryEntity library = knowledgeService.getByName(libraryName);
        if (library == null) {
            return Flux.just("知识库「" + libraryName + "」不存在，请先上传文档。");
        }
        QueryContext context = new QueryContext(library.getId(), question);
        queryChain.handle(context);
        return context.getResponseStream() != null
                ? context.getResponseStream()
                : Flux.just("查询处理失败，请稍后重试。");
    }
}
