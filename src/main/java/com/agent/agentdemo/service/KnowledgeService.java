package com.agent.agentdemo.service;

import com.agent.agentdemo.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeService {

    private final DocumentRepository documentRepository;

    public KnowledgeService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<String> listLibraries() {
        return documentRepository.findDistinctLibraryNames();
    }
}
