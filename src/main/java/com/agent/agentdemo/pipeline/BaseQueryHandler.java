package com.agent.agentdemo.pipeline;

/**
 * 责任链基类，持有对下一节点的引用并提供 passToNext 便捷方法。
 */
public abstract class BaseQueryHandler implements QueryHandler {

    private QueryHandler next;

    public void setNext(QueryHandler next) {
        this.next = next;
    }

    /** 将上下文传递给下一个节点（若存在）。 */
    protected void passToNext(QueryContext context) {
        if (next != null) {
            next.handle(context);
        }
    }
}
