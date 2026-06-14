package com.kbassistant.domain.port.out;

import com.kbassistant.domain.model.MimeType;

public interface TextExtractorPort {
    String extract(byte[] content, MimeType mimeType);
}
