package com.agent.agentdemo.controller;

import com.agent.agentdemo.entity.DocumentEntity;
import com.agent.agentdemo.model.QueryRequest;
import com.agent.agentdemo.service.impl.DocumentServiceImpl;
import com.agent.agentdemo.service.impl.LibraryServiceImpl;
import com.agent.agentdemo.service.impl.QueryPipelineServiceImpl;
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

    private final LibraryServiceImpl knowledgeService;
    private final DocumentServiceImpl documentProcessingService;
    private final QueryPipelineServiceImpl queryPipelineService;

    public KnowledgeController(LibraryServiceImpl knowledgeService,
                               DocumentServiceImpl documentProcessingService,
                               QueryPipelineServiceImpl queryPipelineService) {
        this.knowledgeService          = knowledgeService;
        this.documentProcessingService = documentProcessingService;
        this.queryPipelineService      = queryPipelineService;
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

        queryPipelineService.query(request.libraryName(), request.question())
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

    /**
     * 按 ID 更新文档：内容未变则跳过，否则重新分块 + Embedding
     */
    @PutMapping("/documents/{documentId}")
    public ResponseEntity<?> updateDocument(
            @PathVariable String documentId,
            @RequestParam("file") MultipartFile file) {
        try {
            DocumentEntity doc = documentProcessingService.updateDocument(documentId, file);
            return ResponseEntity.ok(Map.of(
                    "message",     "文档「" + doc.getFilename() + "」更新成功",
                    "documentId",  doc.getId(),
                    "chunkCount",  doc.getChunkCount(),
                    "description", doc.getDescription()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "更新失败：" + e.getMessage()));
        }
    }

    /**
     * 按 ID 删除文档及其所有 chunks
     */
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<?> deleteDocument(@PathVariable String documentId) {
        try {
            documentProcessingService.deleteDocument(documentId);
            return ResponseEntity.ok(Map.of("message", "文档已删除"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "删除失败：" + e.getMessage()));
        }
    }
}
