package com.magicsystems.jrostering.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Servlet filter that enforces a per-IP token-bucket rate limit on all
 * {@code /api/**} requests.
 *
 * <p>Each remote IP address gets an independent Bucket4j token bucket allowing
 * {@value #CAPACITY} requests per minute. Requests that exceed the limit receive
 * HTTP 429 Too Many Requests with a {@code Retry-After} header indicating how many
 * seconds until the next token is available.</p>
 *
 * <p>Buckets are stored in a Caffeine cache (max 10 000 entries, evicted 10 minutes
 * after last access) to bound memory under long-running deployments with many
 * distinct clients. Caffeine's {@code LoadingCache} provides thread-safe
 * atomic bucket creation via {@link Cache#get(Object, java.util.function.Function)}.</p>
 */
@Component
public class ApiRateLimitingFilter extends OncePerRequestFilter {

    static final int CAPACITY = 100;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Bucket bucket = buckets.get(remoteAddr(request), ignored -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1L,
                    probe.getNanosToWaitForRefill() / 1_000_000_000L);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too Many Requests\",\"retryAfterSeconds\":" + retryAfterSeconds + "}");
        }
    }

    private static Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(CAPACITY, Refill.intervally(CAPACITY, Duration.ofMinutes(1))))
                .build();
    }

    private static String remoteAddr(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
