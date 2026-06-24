package com.kbassistant.domain.service;

import java.util.List;

public interface ChunkingStrategy {
    List<String> chunk(String text);
}
