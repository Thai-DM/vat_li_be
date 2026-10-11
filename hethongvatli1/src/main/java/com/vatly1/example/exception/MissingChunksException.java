package com.vatly1.example.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class MissingChunksException extends RuntimeException {

    private final List<Integer> missingChunks;

    public MissingChunksException(String message, List<Integer> missingChunks) {
        super(message);
        this.missingChunks = missingChunks;
    }
}
