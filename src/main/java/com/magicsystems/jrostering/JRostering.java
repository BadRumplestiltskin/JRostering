package com.magicsystems.jrostering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Application entry point for the Universal Staff Rostering Application.
 *
 * <p>JPA auditing ({@code @CreatedDate}, {@code @LastModifiedDate}) is enabled in
 * {@link JpaAuditingConfig} so that {@code @WebMvcTest} slices can exclude it without
 * triggering the JPA metamodel initialisation.</p>
 *
 * <p>{@code @EnableAsync} activates Spring's {@code @Async} executor used by
 * {@code SolverService} to run Timefold solves in the background.</p>
 */
@SpringBootApplication
@EnableAsync
public class JRostering {

    public static void main(String[] args) {
        SpringApplication.run(JRostering.class, args);
    }
}
