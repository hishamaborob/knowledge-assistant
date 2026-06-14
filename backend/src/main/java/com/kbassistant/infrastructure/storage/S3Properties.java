package com.kbassistant.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.s3")
public class S3Properties {

    private String bucketName = "knowledge-assistant-docs";
    private String region = "us-east-1";

    // Null in production (uses real AWS endpoint).
    // Set to http://localhost:4566 in application-local.yml for LocalStack.
    private String endpointOverride;

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getEndpointOverride() { return endpointOverride; }
    public void setEndpointOverride(String endpointOverride) { this.endpointOverride = endpointOverride; }
}
