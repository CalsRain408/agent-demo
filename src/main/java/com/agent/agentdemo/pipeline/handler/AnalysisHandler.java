package com.agent.agentdemo.pipeline.handler;

import com.agent.agentdemo.config.pipeline.HandlerConfig;
import com.agent.agentdemo.config.pipeline.PipelineConfigProvider;
import com.agent.agentdemo.pipeline.BaseQueryHandler;
import com.agent.agentdemo.pipeline.QueryContext;
import com.agent.agentdemo.pipeline.QueryIntent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class AnalysisHandler extends BaseQueryHandler {
    @Resource
    private ChatClient chatClient;

    @Resource
    private PipelineConfigProvider configProvider;


    /**
     * 责任链第一节点：意图解析。
     *
     * 调用 LLM 对用户原始输入进行分析，输出结构化的 {@link QueryIntent}。
     * system prompt、model、temperature 均通过 {@link PipelineConfigProvider} 注入，
     * 修改 {@code application.yaml} 中的 {@code agent.pipeline.analysis} 节点即可调整，无需改动代码。
     *
     * <p>若 LLM 调用失败，intent 保持 null，后续节点自动降级为原始问题检索。
     */
    @Override
    public void handle(QueryContext context) {
        HandlerConfig cfg = configProvider.getAnalysisConfig();
        log.debug("AnalysisHandler: model={} temp={} question='{}'",
                cfg.getModel(), cfg.getTemperature(), context.getOriginalQuestion());
        try {
            QueryIntent intent = chatClient.prompt()
                    .options(cfg.toChatOptions())
                    .system(cfg.getSystemPrompt())
                    .user(context.getOriginalQuestion())
                    .call()
                    .entity(QueryIntent.class);

            context.setIntent(intent);
            log.debug("AnalysisHandler: intent={}", intent);
        } catch (Exception e) {
            log.warn("AnalysisHandler: intent analysis failed, falling back to original question. error={}",
                    e.getMessage());
        }

        passToNext(context);
    }
}
