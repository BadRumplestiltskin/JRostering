package com.magicsystems.jrostering;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async executor configuration for JRostering.
 *
 * <h3>Two-executor model</h3>
 * <ul>
 *   <li><b>Default executor</b> — Spring Boot auto-configures a
 *       {@link org.springframework.core.task.VirtualThreadTaskExecutor} when
 *       {@code spring.threads.virtual.enabled=true}. This handles all general
 *       {@code @Async} tasks and Tomcat request threads.</li>
 *   <li><b>{@code solverExecutor}</b> — A <em>platform-thread</em> pool (2–4
 *       threads) dedicated to the Timefold solver. Timefold's internal search is
 *       CPU-bound and runs continuously without yielding; a virtual thread would
 *       pin a carrier thread for the entire solve duration, starving other virtual
 *       threads of scheduler capacity. Platform threads in a bounded pool are the
 *       correct choice here.</li>
 * </ul>
 *
 * <h3>Security context propagation</h3>
 * <p>The executor is wrapped in {@link DelegatingSecurityContextTaskExecutor}
 * so that the authenticated {@link org.springframework.security.core.context.SecurityContext}
 * is copied from the submitting web thread to the solver thread. Without this,
 * any code on the async thread that calls
 * {@link org.springframework.security.core.context.SecurityContextHolder#getContext()}
 * would receive an empty (unauthenticated) context.</p>
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Dedicated platform-thread pool for CPU-bound Timefold solver runs.
     *
     * <p>Core/max sizes of 2 and 4 allow a small number of concurrent solves
     * (e.g. when multiple sites submit jobs simultaneously) without overwhelming
     * the host CPU. The queue capacity of 10 causes job submissions beyond the
     * active limit to wait rather than spawn unlimited threads.</p>
     *
     * <p>The pool is wrapped in {@link DelegatingSecurityContextTaskExecutor}
     * to propagate the security context to the solver thread.</p>
     */
    @Bean("solverExecutor")
    public Executor solverExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("solver-");
        executor.setDaemon(false);
        executor.initialize();
        return new DelegatingSecurityContextTaskExecutor(executor);
    }
}
