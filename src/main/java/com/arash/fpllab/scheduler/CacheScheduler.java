package com.arash.fpllab.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CacheScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CacheScheduler.class);
    private final CacheManager cacheManager;

    public CacheScheduler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Clears the bootstrap cache daily at 2:00 AM
     * This ensures fresh data is fetched when a new week passes
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void clearBootstrapCache() {
        logger.info("Clearing bootstrap cache...");
        var cache = cacheManager.getCache("bootstrap");
        if (cache != null) {
            cache.clear();
            logger.info("Bootstrap cache cleared successfully");
        } else {
            logger.warn("Bootstrap cache not found in CacheManager");
        }
    }
}

