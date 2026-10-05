package com.click4bonds.app.Modules.Document.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The threads document generation is allowed to use.
 *
 * <p>Named and separate rather than borrowing Boot's {@code applicationTaskExecutor},
 * because the work that runs here is not request work: a LibreOffice conversion
 * occupies its thread for up to the configured timeout, and a basket of those on
 * the shared executor would starve the HTTP requests it exists to serve.</p>
 *
 * <p><strong>Sized from the PDF converter's own limit, not independently.</strong>
 * {@code document.pdf.max-concurrent-conversions} is the number of LibreOffice
 * processes this application will run at once, enforced by a semaphore inside
 * {@code LibreOfficePdfConverter}. The pool is one wider than that so a render
 * queueing for a conversion permit does not also hold the only thread an upload
 * needs — the two run together, and a pool smaller than the work
 * deadlocks.</p>
 */
@Configuration
public class DocumentTaskExecutorConfig {

    /**
     * How many tasks may wait before the pool refuses more.
     *
     * <p>Bounded on purpose: an unbounded queue turns a slow renderer into an
     * out-of-memory error rather than into back-pressure. The callers are Kafka
     * listeners, and a rejected task is better reported than silently
     * accumulated.</p>
     */
    private static final int QUEUE_CAPACITY = 64;

    @Bean(name = "documentTaskExecutor")
    public ThreadPoolTaskExecutor documentTaskExecutor(DocumentProperties properties) {

        /*
         * At least two: the pipeline always runs an upload and a render together,
         * and a pool of one would make the second wait for the first for no
         * reason.
         */
        int poolSize = Math.max(
                2,
                properties.getPdf().getMaxConcurrentConversions() + 1);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("deal-doc-");

        /*
         * A document that was being written when the application stopped is
         * worth waiting a moment for: the upload is the only thing standing
         * between the customer and a letter, and it is already paid for.
         */
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        return executor;
    }
}
