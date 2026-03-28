package com.agent.agentdemo.repository;

import com.agent.agentdemo.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    List<DocumentEntity> findByLibraryNameOrderByUploadTimeDesc(String libraryName);

    @Query("SELECT DISTINCT d.libraryName FROM DocumentEntity d ORDER BY d.libraryName")
    List<String> findDistinctLibraryNames();
}
