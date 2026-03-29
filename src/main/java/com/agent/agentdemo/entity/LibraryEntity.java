package com.agent.agentdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("libraries")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LibraryEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 知识库名称，全局唯一 */
    private String name;
}
