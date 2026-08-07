package org.mingharness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mingharness.model.ModelConfig;
import org.mingharness.config.RedisProperties;
import org.mingharness.config.MessagingProperties;
import org.mingharness.runtime.application.RuntimeLimits;
import org.mingharness.security.HarnessAuthProperties;
import org.mingharness.audit.AuditIntegrityProperties;
import org.mingharness.config.DataRetentionProperties;
import org.mingharness.config.WorkspaceProperties;
import org.mingharness.config.ContextChunkingProperties;
import org.mingharness.config.EmbeddingProperties;

@SpringBootApplication
@EnableConfigurationProperties({ModelConfig.class, RuntimeLimits.class, RedisProperties.class,
        MessagingProperties.class, HarnessAuthProperties.class, AuditIntegrityProperties.class,
        DataRetentionProperties.class, WorkspaceProperties.class, ContextChunkingProperties.class,
        EmbeddingProperties.class})
@EnableScheduling
public class MingHarnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(MingHarnessApplication.class, args);
    }

}
