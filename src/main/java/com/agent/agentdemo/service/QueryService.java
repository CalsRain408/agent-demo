package com.agent.agentdemo.service;

import reactor.core.publisher.Flux;

public interface QueryService {
    Flux<String> query(String libraryName, String question);
}
