package com.agent.agentdemo.controller;

import com.agent.agentdemo.model.QueryRequest;
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

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("libraryName") String libraryName,
            @RequestParam("file") MultipartFile file) {
        try {
            knowledgeService.ingest(libraryName, file);
            return ResponseEntity.ok(Map.of(
                    "message", "文档「" + file.getOriginalFilename() + "」已成功加入知识库「" + libraryName + "」"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "上传失败：" + e.getMessage()));
        }
    }

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

    @GetMapping("/libraries")
    public ResponseEntity<List<String>> libraries() {
        return ResponseEntity.ok(knowledgeService.listLibraries());
    }
}
