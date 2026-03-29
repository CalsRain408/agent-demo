package com.agent.agentdemo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.agent.agentdemo.mapper")
public class AgentDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentDemoApplication.class, args);
    }

}
