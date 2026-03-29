package com.agent.agentdemo.service;

import com.agent.agentdemo.entity.DocumentEntity;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService extends IService<DocumentEntity> {
    DocumentEntity process(String libraryName, MultipartFile file);

    List<DocumentEntity> listByLibrary(String libraryName);
}
