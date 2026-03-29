package com.agent.agentdemo.service;

import com.agent.agentdemo.entity.DocumentEntity;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService extends IService<DocumentEntity> {
    DocumentEntity process(String libraryName, MultipartFile file);

    List<DocumentEntity> listByLibrary(String libraryName);

    /** 按 ID 更新文档：若内容未变则跳过，否则重新分块 + Embedding */
    DocumentEntity updateDocument(String documentId, MultipartFile file);

    /** 按 ID 删除文档及其所有 chunks */
    void deleteDocument(String documentId);
}
