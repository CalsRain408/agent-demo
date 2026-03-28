package com.agent.agentdemo.service;

import com.agent.agentdemo.repository.DocumentRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final DocumentRepository documentRepository;

    public KnowledgeService(VectorStore vectorStore,
                            ChatClient.Builder chatClientBuilder,
                            DocumentRepository documentRepository) {
        this.vectorStore        = vectorStore;
        this.chatClient         = chatClientBuilder.build();
        this.documentRepository = documentRepository;
    }

    public Flux<String> queryStream(String libraryName, String question) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .filterExpression(b.eq("library_name", libraryName).build())
                        .build()
        );

        if (docs.isEmpty()) {
            return Flux.just("在知识库「" + libraryName + "」中未找到与该问题相关的内容。");
        }

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        return chatClient.prompt()
                .system("""
                        你是一个专业的文档问答助手。请严格根据以下参考文档内容回答用户的问题。

                        要求：
                        1. 严格基于文档内容作答，不编造文档中没有的信息
                        2. 如果文档中没有相关信息，请直接告知用户
                        3. 回答要清晰、准确

                        参考文档：
                        """ + context)
                .user(question)
                .stream()
                .content();
    }

    public List<String> listLibraries() {
        return documentRepository.findDistinctLibraryNames();
    }
}
