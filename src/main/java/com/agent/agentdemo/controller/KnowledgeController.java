package com.agent.agentdemo.controller;

import com.agent.agentdemo.entity.DocumentEntity;
import com.agent.agentdemo.model.QueryRequest;
import com.agent.agentdemo.service.DocumentProcessingService;
import com.agent.agentdemo.service.KnowledgeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final DocumentProcessingService documentProcessingService;

    public KnowledgeController(KnowledgeService knowledgeService,
                               DocumentProcessingService documentProcessingService) {
        this.knowledgeService           = knowledgeService;
        this.documentProcessingService  = documentProcessingService;
    }

    /**
     * 上传文档：解析 → 生成摘要 → 分块 → Embedding → 存储
     */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("libraryName") String libraryName,
            @RequestParam("file") MultipartFile file) {
        try {
            DocumentEntity doc = documentProcessingService.process(libraryName, file);
            return ResponseEntity.ok(Map.of(
                    "message",    "文档「" + doc.getFilename() + "」已成功处理并加入知识库「" + libraryName + "」",
                    "documentId", doc.getId(),
                    "chunkCount", doc.getChunkCount(),
                    "description", doc.getDescription()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "处理失败：" + e.getMessage()));
        }
    }

    /**
     * 流式问答
     */
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseBodyEmitter streamQuery(@RequestBody QueryRequest request) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(120_000L);

        knowledgeService.queryStream(request.libraryName(), request.question())
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        return emitter;
    }

    /**
     * 知识库列表（从 documents 表去重）
     */
    @GetMapping("/libraries")
    public ResponseEntity<List<String>> libraries() {
        return ResponseEntity.ok(knowledgeService.listLibraries());
    }

    /**
     * 指定知识库下的文档列表
     */
    @GetMapping("/documents")
    public ResponseEntity<List<DocumentEntity>> documents(@RequestParam String libraryName) {
        return ResponseEntity.ok(documentProcessingService.listByLibrary(libraryName));
    }
}
