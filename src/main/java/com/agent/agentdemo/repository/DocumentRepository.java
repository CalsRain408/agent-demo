package com.agent.agentdemo.repository;

import com.agent.agentdemo.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    List<DocumentEntity> findByLibraryNameOrderByUploadTimeDesc(String libraryName);

    @Query("SELECT DISTINCT d.libraryName FROM DocumentEntity d ORDER BY d.libraryName")
    List<String> findDistinctLibraryNames();

    /** 粗筛：获取知识库下所有文档 ID */
    @Query("SELECT d.id FROM DocumentEntity d WHERE d.libraryName = :libraryName")
    List<String> findIdsByLibraryName(@Param("libraryName") String libraryName);

    /** 粗筛：获取知识库下指定文件类型的文档 ID */
    @Query("SELECT d.id FROM DocumentEntity d WHERE d.libraryName = :libraryName AND d.fileType IN :fileTypes")
    List<String> findIdsByLibraryNameAndFileTypes(@Param("libraryName") String libraryName,
                                                  @Param("fileTypes") Collection<String> fileTypes);
}
