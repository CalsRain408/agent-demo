package com.agent.agentdemo.config.pipeline;

/**
 * Pipeline 配置的统一访问接口。
 *
 * 默认实现 {@link DefaultPipelineConfigProvider} 从 {@code application.yaml} 读取配置。
 * 若需要运行时动态修改（不重启服务），可提供一个 {@code @Primary} 的数据库实现：
 *
 * <pre>
 * {@code
 * @Primary
 * @Component
 * public class DatabasePipelineConfigProvider implements PipelineConfigProvider {
 *     // 从 DB / Redis / 配置中心读取，可热更新
 * }
 * }
 * </pre>
 */
public interface PipelineConfigProvider {

    /** 意图解析节点（AnalysisHandler）的配置 */
    HandlerConfig getAnalysisConfig();

    /** 回答生成节点（ResponseHandler）的配置 */
    HandlerConfig getResponseConfig();

    /** 向量检索节点（ToolCallHandler）的配置 */
    RetrievalConfig getRetrievalConfig();
}
