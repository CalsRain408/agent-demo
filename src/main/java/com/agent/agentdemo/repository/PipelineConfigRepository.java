package com.agent.agentdemo.repository;

import com.agent.agentdemo.entity.PipelineConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PipelineConfigRepository extends JpaRepository<PipelineConfigEntity, String> {}
