package org.mingharness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.mingharness.model.ModelConfig;
import org.mingharness.runtime.application.RuntimeLimits;

@SpringBootApplication
@EnableConfigurationProperties({ModelConfig.class, RuntimeLimits.class})
public class MingHarnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(MingHarnessApplication.class, args);
    }

}
