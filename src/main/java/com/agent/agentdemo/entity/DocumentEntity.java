package com.agent.agentdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("documents")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属知识库 ID，关联 libraries.id */
    private String libraryId;

    private String filename;

    private String fileType;

    private Long fileSize;

    private LocalDateTime uploadTime;

    /** LLM 生成的语义描述 */
    private String description;

    private Integer chunkCount;

    /** 文件内容 MD5，用于增量更新判断 */
    private String contentHash;
}
