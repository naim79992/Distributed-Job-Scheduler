package com.scheduler.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI distributedJobSchedulerOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Distributed Job Scheduler API")
                        .description("REST API documentation for the Distributed Job Scheduler cluster nodes. " +
                                "This scheduler handles distributed cron jobs and one-time tasks across multiple nodes.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Backend Team")
                                .url("https://github.com/naim79992/Distributed-Job-Scheduler"))
                        .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT")));
    }
}
