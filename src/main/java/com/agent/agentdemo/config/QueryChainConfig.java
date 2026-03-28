package com.agent.agentdemo.config;

import com.agent.agentdemo.pipeline.QueryHandler;
import com.agent.agentdemo.pipeline.handler.AnalysisHandler;
import com.agent.agentdemo.pipeline.handler.ResponseHandler;
import com.agent.agentdemo.pipeline.handler.ToolCallHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryChainConfig {

    /**
     * 装配查询责任链：AnalysisHandler → ToolCallHandler → ResponseHandler
     */
    @Bean
    public QueryHandler queryChain(AnalysisHandler analysisHandler,
                                   ToolCallHandler toolCallHandler,
                                   ResponseHandler responseHandler) {
        analysisHandler.setNext(toolCallHandler);
        toolCallHandler.setNext(responseHandler);
        return analysisHandler;
    }
}
