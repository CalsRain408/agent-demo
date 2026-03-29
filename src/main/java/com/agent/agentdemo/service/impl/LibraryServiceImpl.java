package com.agent.agentdemo.service.impl;

import com.agent.agentdemo.entity.LibraryEntity;
import com.agent.agentdemo.mapper.LibraryMapper;
import com.agent.agentdemo.service.LibraryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LibraryServiceImpl extends ServiceImpl<LibraryMapper, LibraryEntity> implements LibraryService {

    @Override
    public List<String> listLibraries() {
        return this.lambdaQuery()
                .orderByAsc(LibraryEntity::getName)
                .list()
                .stream()
                .map(LibraryEntity::getName)
                .collect(Collectors.toList());
    }

    @Override
    public LibraryEntity getOrCreate(String name) {
        LibraryEntity existing = getByName(name);
        if (existing != null) return existing;
        LibraryEntity library = new LibraryEntity();
        library.setId(UUID.randomUUID().toString());
        library.setName(name);
        this.save(library);
        return library;
    }

    @Override
    public LibraryEntity getByName(String name) {
        return this.lambdaQuery()
                .eq(LibraryEntity::getName, name)
                .one();
    }
}
