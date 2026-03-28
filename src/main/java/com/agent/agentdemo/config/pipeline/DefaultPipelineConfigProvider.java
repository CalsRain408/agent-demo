package com.agent.agentdemo.config.pipeline;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 基于 {@code application.yaml} 的默认配置实现。
 *
 * Spring Boot 在启动时将 {@code agent.pipeline.*} 下的属性绑定到本类，
 * 修改 YAML 并重启即可生效。
 * 若需要不重启热更新，可接入 Spring Cloud Config 并在本类加 {@code @RefreshScope}
 * 或实现 {@link PipelineConfigProvider} 的数据库版本并标记 {@code @Primary}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.pipeline")
public class DefaultPipelineConfigProvider implements PipelineConfigProvider {

    private HandlerConfig analysis  = new HandlerConfig();
    private HandlerConfig response  = new HandlerConfig();
    private RetrievalConfig retrieval = new RetrievalConfig();

    @Override public HandlerConfig    getAnalysisConfig()  { return analysis; }
    @Override public HandlerConfig    getResponseConfig()  { return response; }
    @Override public RetrievalConfig  getRetrievalConfig() { return retrieval; }
}
