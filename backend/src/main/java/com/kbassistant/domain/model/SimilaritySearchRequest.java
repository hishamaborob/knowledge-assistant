package com.kbassistant.domain.model;

import java.util.List;
import java.util.Objects;

public final class SimilaritySearchRequest {

    private final float[] queryVector;
    private final int topK;
    private final double similarityThreshold;
    private final List<DocumentId> documentIds; // empty = search all documents

    private SimilaritySearchRequest(float[] queryVector,
                                    int topK,
                                    double similarityThreshold,
                                    List<DocumentId> documentIds) {
        this.queryVector = queryVector;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.documentIds = List.copyOf(documentIds);
    }

    public static Builder builder() { return new Builder(); }

    public float[] queryVector()          { return queryVector; }
    public int topK()                     { return topK; }
    public double similarityThreshold()   { return similarityThreshold; }
    public List<DocumentId> documentIds() { return documentIds; }
    public boolean hasDocumentFilter()    { return !documentIds.isEmpty(); }

    public static final class Builder {
        private float[] queryVector;
        private int topK = 10;
        private double similarityThreshold = 0.75;
        private List<DocumentId> documentIds = List.of();

        public Builder queryVector(float[] v)              { this.queryVector = Objects.requireNonNull(v); return this; }
        public Builder topK(int k)                         { this.topK = k; return this; }
        public Builder similarityThreshold(double t)       { this.similarityThreshold = t; return this; }
        public Builder documentIds(List<DocumentId> ids)   { this.documentIds = ids; return this; }

        public SimilaritySearchRequest build() {
            Objects.requireNonNull(queryVector, "queryVector is required");
            return new SimilaritySearchRequest(queryVector, topK, similarityThreshold, documentIds);
        }
    }
}
