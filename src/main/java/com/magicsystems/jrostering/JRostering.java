package com.magicsystems.jrostering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Application entry point for the Universal Staff Rostering Application.
 *
 * <p>{@code @EnableJpaAuditing} activates Spring Data's {@code @CreatedDate} and
 * {@code @LastModifiedDate} support used by all auditable domain entities.</p>
 *
 * <p>{@code @EnableAsync} activates Spring's {@code @Async} executor used by
 * {@code SolverService} to run Timefold solves in the background.</p>
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class JRostering {

    public static void main(String[] args) {
        SpringApplication.run(JRostering.class, args);
    }
}
