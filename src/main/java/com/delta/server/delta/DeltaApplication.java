package com.delta.server.delta;

import com.delta.server.delta.main.GracefulShutdown;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;


@SpringBootApplication(scanBasePackages = {
        "com.delta.server.delta.service",
        "com.delta.server.delta.repo",    // <–– include your repo package
        "com.delta.server.delta.controller",
        "com.delta.server.delta.config",
        "com.delta.server.delta.strategy"
})
@EnableR2dbcRepositories(basePackages = "com.delta.server.delta.repo")
@Slf4j
@EnableScheduling
public class DeltaApplication {

    public static void main(String[] args) throws IOException {
        SpringApplication.run(DeltaApplication.class, args);
        log.debug("Application started");
    }

    @Bean
    public GracefulShutdown gracefulShutdown() {
        return new GracefulShutdown();
    }

    @Bean
    public TomcatServletWebServerFactory tomcatFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addConnectorCustomizers(gracefulShutdown());
        return factory;
    }


}
