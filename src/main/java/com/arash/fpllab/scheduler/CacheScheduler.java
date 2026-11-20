package com.arash.fpllab.scheduler;

import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The bootstrap cache is refreshed by SyncService every 30 minutes;
 * league table caches are refreshed here.
 */
@Component
public class CacheScheduler {

    private final CacheManager cacheManager;

    public CacheScheduler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Clears the league table caches every 2 minutes so the table
     * picks up new results on matchdays.
     */
    @Scheduled(fixedRate = 2 * 60 * 1000)
    public void clearStandingsCaches() {
        for (String name : new String[]{"fixtures", "liveStandings"}) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}

