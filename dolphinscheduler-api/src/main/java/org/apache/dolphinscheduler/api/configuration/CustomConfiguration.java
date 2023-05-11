package org.apache.dolphinscheduler.api.configuration;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("custom")
public class CustomConfiguration {

    @Data
    @Configuration
    @ConfigurationProperties("custom.gitlab")
    public class GitlabConfiguration {

        private String accessToken;
    }

    @Data
    @Configuration
    @ConfigurationProperties("custom.resources")
    public class ResourcesConfiguration {

        private String prefix;
    }

    @Data
    @Configuration
    @ConfigurationProperties("custom.ds")
    public class DSConfiguration {

        private String admin;
    }

}
