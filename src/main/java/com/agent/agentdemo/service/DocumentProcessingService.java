package com.agent.agentdemo.service;

import com.agent.agentdemo.entity.DocumentEntity;
import com.agent.agentdemo.repository.DocumentRepository;
import com.agent.agentdemo.utils.CodeMetadataExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户上传文档的处理流程：
 *   解析 → 结构化标签 → 语义标签（LLM） → 入库 → 分块 → 元数据注入 → Embedding → PGVector
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    /** 截取文档前 N 个字符用于生成摘要（约 300‑500 token） */
    private static final int DESCRIPTION_PREVIEW_CHARS = 1500;

    private final DocumentRepository documentRepository;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final CodeMetadataExtractor codeMetadataExtractor;

    public DocumentProcessingService(DocumentRepository documentRepository,
                                     VectorStore vectorStore,
                                     ChatClient.Builder chatClientBuilder,
                                     CodeMetadataExtractor codeMetadataExtractor) {
        this.documentRepository   = documentRepository;
        this.vectorStore          = vectorStore;
        this.chatClient           = chatClientBuilder.build();
        this.codeMetadataExtractor = codeMetadataExtractor;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 主流程
    // ─────────────────────────────────────────────────────────────────────────

    public DocumentEntity process(String libraryName, MultipartFile file) {
        // 1. 解析原始文档
        List<Document> rawDocs = parseDocument(file);
        String fullText = rawDocs.stream().map(Document::getText).collect(Collectors.joining("\n"));

        // 2. 结构化标签
        String fileType = detectFileType(file.getOriginalFilename());
        DocumentEntity entity = buildEntity(libraryName, file, fileType);

        // 3. 语义标签：LLM 生成摘要
        log.info("Generating description for '{}' ...", file.getOriginalFilename());
        entity.setDescription(generateDescription(fullText, fileType));

        // 4. 存入 documents 表，拿到持久化 ID
        entity = documentRepository.save(entity);
        log.info("Saved document entity id={}", entity.getId());

        // 5. 分块
        List<Document> chunks = chunk(rawDocs, fileType);

        // 6. 为每个 chunk 注入元数据
        enrichChunks(chunks, entity);

        // 7. Embedding → PGVector
        log.info("Embedding {} chunks for document id={}", chunks.size(), entity.getId());
        vectorStore.add(chunks);

        // 8. 回写 chunkCount
        entity.setChunkCount(chunks.size());
        return documentRepository.save(entity);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 步骤实现
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 解析原始文件
     * @param file
     * @return
     */
    private List<Document> parseDocument(MultipartFile file) {
        return new TikaDocumentReader(file.getResource()).get();
    }

    /** 结构化标签：从文件本身 + 请求参数获取 */
    private DocumentEntity buildEntity(String libraryName, MultipartFile file, String fileType) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setLibraryName(libraryName);
        entity.setFilename(file.getOriginalFilename());
        entity.setFileType(fileType);
        entity.setFileSize(file.getSize());
        entity.setUploadTime(LocalDateTime.now());
        return entity;
    }

    /** 语义标签：取前 N 字符，调用 LLM 生成简短描述 */
    private String generateDescription(String fullText, String fileType) {
        String preview = fullText.length() > DESCRIPTION_PREVIEW_CHARS
                ? fullText.substring(0, DESCRIPTION_PREVIEW_CHARS)
                : fullText;

        String typeHint = codeMetadataExtractor.isCodeFile(fileType)
                ? "这是一个 " + fileType.toUpperCase() + " 代码文件。"
                : "";

        return chatClient.prompt()
                .system("你是一个文档分析助手。" + typeHint
                        + "请根据文档内容，用 2‑3 句话简洁描述其主要内容和用途。不要使用 Markdown 格式。")
                .user(preview)
                .call()
                .content();
    }

    /**
     * 分块策略：
     * TokenTextSplitter 构造器：
     *   TokenTextSplitter(defaultChunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator)
     *   注意：TokenTextSplitter 本身不支持 overlap，如需 overlap 需自行后处理。
     *
     * 尺寸选择理由：
     *   - 代码文件用更大的 chunk（1024 token），因为一个方法/类的语义单元往往很长，
     *     切太小容易从中间断开，导致检索到残缺的上下文。
     *   - 文本文档（PDF/Word 等）用 512 token，通常能覆盖 1-3 个完整段落，
     *     语义边界更自然，不需要过大的窗口。
     */
    private List<Document> chunk(List<Document> docs, String fileType) {
        TokenTextSplitter splitter = codeMetadataExtractor.isCodeFile(fileType)
                ? new TokenTextSplitter(1024, 100, 5, 10000, true)
                : new TokenTextSplitter(512,  100, 5, 10000, true);
        return splitter.apply(docs);
    }

    /**
     * 为每个 chunk 写入通用元数据 + 代码专属元数据，
     * 这些字段最终存入 PGVector 的 metadata JSONB 列，支持过滤查询。
     */
    private void enrichChunks(List<Document> chunks, DocumentEntity entity) {
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = chunks.get(i).getMetadata();

            // 通用字段
            meta.put("document_id",  entity.getId());
            meta.put("library_name", entity.getLibraryName());
            meta.put("filename",     entity.getFilename());
            meta.put("file_type",    entity.getFileType());
            meta.put("chunk_index",  i);

            // 代码专属字段（class_name / package / function_name 等）
            if (codeMetadataExtractor.isCodeFile(entity.getFileType())) {
                Map<String, Object> codeMeta = codeMetadataExtractor
                        .extract(chunks.get(i).getText(), entity.getFileType());
                meta.putAll(codeMeta);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 查询辅助
    // ─────────────────────────────────────────────────────────────────────────

    public List<DocumentEntity> listByLibrary(String libraryName) {
        return documentRepository.findByLibraryNameOrderByUploadTimeDesc(libraryName);
    }

    private String detectFileType(String filename) {
        if (filename == null || !filename.contains(".")) return "txt";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
