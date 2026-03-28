package com.agent.agentdemo.pipeline;

/**
 * 责任链节点接口。
 */
public interface QueryHandler {
    void handle(QueryContext context);
}
