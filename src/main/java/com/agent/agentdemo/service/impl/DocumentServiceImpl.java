package com.agent.agentdemo.service.impl;

import com.agent.agentdemo.entity.DocumentEntity;
import com.agent.agentdemo.mapper.DocumentMapper;
import com.agent.agentdemo.entity.LibraryEntity;
import com.agent.agentdemo.service.DocumentService;
import com.agent.agentdemo.service.LibraryService;
import com.agent.agentdemo.utils.CodeMetadataExtractor;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户上传文档的处理流程：
 *   解析 → 结构化标签 → 语义标签（LLM） → 入库 → 分块 → 元数据注入 → Embedding → PGVector
 */
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, DocumentEntity> implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    /** 截取文档前 N 个字符用于生成摘要（约 300‑500 token） */
    private static final int DESCRIPTION_PREVIEW_CHARS = 1500;

    @Autowired
    private VectorStore vectorStore;

    @Resource
    private ChatClient chatClient;

    @Autowired
    private CodeMetadataExtractor codeMetadataExtractor;

    @Autowired
    private LibraryService libraryService;


    // ─────────────────────────────────────────────────────────────────────────
    // 主流程
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DocumentEntity process(String libraryName, MultipartFile file) {
        // 0. 计算文件内容 MD5
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file bytes", e);
        }
        String contentHash = computeMd5(fileBytes);

        // 获取或创建知识库（保证 library_name 唯一）
        LibraryEntity library = libraryService.getOrCreate(libraryName);
        String libraryId = library.getId();

        // 增量更新检查：同一知识库 + 文件名已存在记录
        DocumentEntity existingEntity = this.lambdaQuery()
                .eq(DocumentEntity::getLibraryId, libraryId)
                .eq(DocumentEntity::getFilename, file.getOriginalFilename())
                .one();
        if (existingEntity != null) {
            if (contentHash.equals(existingEntity.getContentHash())) {
                log.info("File '{}' content unchanged (hash={}), skipping re-processing",
                        file.getOriginalFilename(), contentHash);
                return existingEntity;
            }
            // 内容变化：清理旧 chunks 和旧记录
            log.info("File '{}' content changed, removing old data for document id={}",
                    file.getOriginalFilename(), existingEntity.getId());
            deleteChunksByDocumentId(existingEntity.getId());
            this.removeById(existingEntity.getId());
        }

        // 1. 解析原始文档
        List<org.springframework.ai.document.Document> rawDocs = parseDocument(file);
        String fullText = rawDocs.stream().map(org.springframework.ai.document.Document::getText).collect(Collectors.joining("\n"));

        // 2. 结构化标签
        String fileType = detectFileType(file.getOriginalFilename());
        DocumentEntity entity = buildEntity(libraryId, file, fileType, contentHash);

        // 3. 语义标签：LLM 生成摘要
        log.info("Generating description for '{}' ...", file.getOriginalFilename());
        entity.setDescription(generateDescription(fullText, fileType));

        // 4. 存入 documents 表
        this.save(entity);
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
        this.updateById(entity);
        return entity;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 步骤实现
    // ─────────────────────────────────────────────────────────────────────────

    private List<Document> parseDocument(MultipartFile file) {
        return new TikaDocumentReader(file.getResource()).get();
    }

    private DocumentEntity buildEntity(String libraryId, MultipartFile file, String fileType, String contentHash) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setLibraryId(libraryId);
        entity.setFilename(file.getOriginalFilename());
        entity.setFileType(fileType);
        entity.setFileSize(file.getSize());
        entity.setUploadTime(LocalDateTime.now());
        entity.setContentHash(contentHash);
        return entity;
    }

    /** 删除 PGVector 中指定文档的所有 chunks */
    private void deleteChunksByDocumentId(String documentId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        vectorStore.delete(b.eq("document_id", documentId).build());
        log.info("Deleted chunks from vector store for document id={}", documentId);
    }

    /** 计算字节数组的 MD5 十六进制字符串 */
    private String computeMd5(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
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
     *
     * 尺寸选择理由：
     *   - 代码文件用更大的 chunk（1024 token），因为一个方法/类的语义单元往往很长。
     *   - 文本文档（PDF/Word 等）用 512 token，通常能覆盖 1-3 个完整段落。
     */
    private List<org.springframework.ai.document.Document> chunk(List<org.springframework.ai.document.Document> docs, String fileType) {
        TokenTextSplitter splitter = codeMetadataExtractor.isCodeFile(fileType)
                ? new TokenTextSplitter(1024, 100, 5, 10000, true)
                : new TokenTextSplitter(512,  100, 5, 10000, true);
        return splitter.apply(docs);
    }

    /**
     * 为每个 chunk 写入通用元数据 + 代码专属元数据，
     * 这些字段最终存入 PGVector 的 metadata JSONB 列，支持过滤查询。
     */
    private void enrichChunks(List<org.springframework.ai.document.Document> chunks, DocumentEntity entity) {
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = chunks.get(i).getMetadata();

            // 通用字段
            meta.put("document_id",  entity.getId());
            meta.put("library_id",   entity.getLibraryId());
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

    @Override
    public List<DocumentEntity> listByLibrary(String libraryName) {
        LibraryEntity library = libraryService.getByName(libraryName);
        if (library == null) return List.of();
        return this.lambdaQuery()
                .eq(DocumentEntity::getLibraryId, library.getId())
                .orderByDesc(DocumentEntity::getUploadTime)
                .list();
    }

    private String detectFileType(String filename) {
        if (filename == null || !filename.contains(".")) return "txt";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
