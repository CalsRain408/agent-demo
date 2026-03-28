package com.agent.agentdemo.pipeline.handler;

import com.agent.agentdemo.pipeline.BaseQueryHandler;
import com.agent.agentdemo.pipeline.QueryContext;
import com.agent.agentdemo.pipeline.QueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 责任链第一节点：意图解析。
 *
 * 调用 LLM 对用户原始输入进行分析，输出结构化的 {@link QueryIntent}：
 * - refinedQuery：去除口语化措辞后的精炼检索语句
 * - intentType：意图分类
 * - summary：一句话意图描述，供后续节点构建更精准的 prompt
 *
 * 若 LLM 调用失败，intent 保持 null，{@link ToolCallHandler} 会自动降级为原始问题检索。
 */
@Component
public class AnalysisHandler extends BaseQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(AnalysisHandler.class);

    private final ChatClient chatClient;

    public AnalysisHandler(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public void handle(QueryContext context) {
        log.debug("AnalysisHandler: analyzing intent for question='{}'", context.getOriginalQuestion());
        try {
            QueryIntent intent = chatClient.prompt()
                    .system("""
                            你是一个检索意图分析助手。请分析用户的问题，完成以下几件事：
                            1. 将问题精炼为适合向量数据库检索的语句（去除语气词、提取核心语义）→ refinedQuery
                            2. 判断意图类型：factual（事实查询）、procedural（操作步骤）、conceptual（概念解释）、other → intentType
                            3. 用一句话描述用户意图 → summary
                            4. 推断问题涉及的文件类型，如 ["java"]、["py","js"]；若无法判断则返回空列表 → suggestedFileTypes
                            5. 若问题明确提到某个类名（如"UserService 类"），提取到 targetClassName；否则返回 null
                            6. 若问题明确提到某个方法/函数名（如"getUserById 方法"），提取到 targetFunctionName；否则返回 null
                            """)
                    .user(context.getOriginalQuestion())
                    .call()
                    .entity(QueryIntent.class);

            context.setIntent(intent);
            log.debug("AnalysisHandler: intent={}", intent);
        } catch (Exception e) {
            // 降级：intent 为 null，后续节点使用原始问题
            log.warn("AnalysisHandler: intent analysis failed, will fall back to original question. error={}", e.getMessage());
        }

        passToNext(context);
    }
}
