package com.kbassistant.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean
    public S3Client s3Client(S3Properties props) {
        var builder = S3Client.builder()
                .region(Region.of(props.getRegion()));

        if (props.getEndpointOverride() != null && !props.getEndpointOverride().isBlank()) {
            // LocalStack: path-style addressing required; dummy credentials accepted.
            builder.endpointOverride(URI.create(props.getEndpointOverride()))
                   .forcePathStyle(true)
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("test", "test")));
        } else {
            // Production: ECS task role or env-var credentials via the default chain.
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
