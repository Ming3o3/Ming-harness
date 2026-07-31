package org.mingharness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mingharness.model.ModelConfig;
import org.mingharness.config.RedisProperties;
import org.mingharness.runtime.application.RuntimeLimits;

@SpringBootApplication
@EnableConfigurationProperties({ModelConfig.class, RuntimeLimits.class, RedisProperties.class})
@EnableScheduling
public class MingHarnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(MingHarnessApplication.class, args);
    }

}
