package com.kbassistant.domain.exception;

import com.kbassistant.domain.model.MimeType;

import java.util.Arrays;
import java.util.stream.Collectors;

public class UnsupportedMimeTypeException extends RuntimeException {

    public UnsupportedMimeTypeException(String detected) {
        super("Unsupported file type '" + detected + "'. Accepted: "
                + Arrays.stream(MimeType.values())
                        .map(m -> m.extension() + " (" + m.mimeString() + ")")
                        .collect(Collectors.joining(", ")));
    }
}
