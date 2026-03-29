package com.agent.agentdemo.mapper;

import com.agent.agentdemo.entity.LibraryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LibraryMapper extends BaseMapper<LibraryEntity> {
    @Select("SELECT * FROM libraries WHERE id = #{libraryID}")
    LibraryEntity getById(@Param("libraryId") String libraryId);
}
