package com.magicsystems.jrostering;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activates Spring Data JPA auditing ({@code @CreatedDate}, {@code @LastModifiedDate}).
 *
 * Kept in a separate {@code @Configuration} class so that {@code @WebMvcTest} slices
 * can exclude it via {@code @WebMvcTest(excludeAutoConfiguration = ...)} without pulling
 * in the JPA metamodel, which is not available in the MVC-only test context.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
