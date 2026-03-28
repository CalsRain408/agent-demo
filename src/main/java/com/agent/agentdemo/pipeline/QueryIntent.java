package com.agent.agentdemo.pipeline;

import java.util.List;

/**
 * LLM 对用户输入的结构化意图解析结果。
 * Spring AI entity() 会自动将字段信息注入 prompt 的格式要求，并将 LLM 返回的 JSON 反序列化为该 record。
 *
 * @param refinedQuery         精炼后的检索语句，去除口语化措辞，突出核心语义，用于向量检索
 * @param intentType           意图类型：factual | procedural | conceptual | other
 * @param summary              一句话描述用户意图，供 ResponseHandler 构建精准 system prompt
 * @param suggestedFileTypes   推断涉及的文件类型，如 ["java","py"]；无法判断时返回空列表
 * @param targetClassName      用户明确提到的类名（如 "UserService"）；未提及时为 null
 * @param targetFunctionName   用户明确提到的方法/函数名（如 "getUserById"）；未提及时为 null
 */
public record QueryIntent(
        String refinedQuery,
        String intentType,
        String summary,
        List<String> suggestedFileTypes,
        String targetClassName,
        String targetFunctionName
) {}
