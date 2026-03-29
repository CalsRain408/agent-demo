package com.agent.agentdemo.service;

import com.agent.agentdemo.entity.LibraryEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface LibraryService extends IService<LibraryEntity> {
    List<String> listLibraries();

    /** 按名称查找知识库，不存在时自动创建 */
    LibraryEntity getOrCreate(String name);

    /** 按名称查找知识库，不存在时返回 null */
    LibraryEntity getByName(String name);
}
