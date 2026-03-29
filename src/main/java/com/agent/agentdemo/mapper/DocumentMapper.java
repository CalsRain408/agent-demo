package com.agent.agentdemo.mapper;

import com.agent.agentdemo.entity.DocumentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface DocumentMapper extends BaseMapper<DocumentEntity> {

    /** 粗筛：知识库下所有文档 ID */
    @Select("SELECT id FROM documents WHERE library_id = #{libraryId}")
    List<String> selectIdsByLibraryId(@Param("libraryId") String libraryId);

    /** 粗筛：知识库下指定文件类型的文档 ID */
    @Select("<script>" +
            "SELECT id FROM documents WHERE library_id = #{libraryId} " +
            "AND file_type IN <foreach item='t' collection='fileTypes' open='(' separator=',' close=')'>#{t}</foreach>" +
            "</script>")
    List<String> selectIdsByLibraryIdAndFileTypes(@Param("libraryId") String libraryId,
                                                  @Param("fileTypes") Collection<String> fileTypes);
}
