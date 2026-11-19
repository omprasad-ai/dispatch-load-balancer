package com.dispatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dispatchOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Dispatch Load Balancer API")
                .version("1.0.0")
                .description("""
                        Accepts delivery orders and fleet details, then returns an optimized dispatch plan.

                        Rules: orders are allocated HIGH -> MEDIUM -> LOW priority, a vehicle never exceeds its \
                        capacity, and distances use the Haversine formula (kilometres). Orders that cannot be \
                        assigned are reported with a reason in `unassignedOrders`."""));
    }
}
