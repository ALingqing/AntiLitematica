package top.chenray.antilitematica.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pure in-memory detection/punishment statistics counter.
 * Tracks totals since plugin start — no disk I/O, no retention cleanup.
 */
public final class StatsTracker {

    private final boolean enabled;
    private final AtomicInteger detections = new AtomicInteger(0);
    private final AtomicInteger punishments = new AtomicInteger(0);
    private final AtomicLong lastDetectionTime = new AtomicLong(0);
    private final AtomicLong lastPunishmentTime = new AtomicLong(0);

    public StatsTracker(boolean enabled) {
        this.enabled = enabled;
    }

    /** No-op shutdown. Kept for API compatibility. */
    public void shutdown() {
        // nothing to clean up
    }

    /** Record a detection event. */
    public void recordDetection() {
        if (!enabled) return;
        detections.incrementAndGet();
        lastDetectionTime.set(System.currentTimeMillis());
    }

    /** Record a punishment event. */
    public void recordPunishment() {
        if (!enabled) return;
        punishments.incrementAndGet();
        lastPunishmentTime.set(System.currentTimeMillis());
    }

    /** Get total detections since plugin start. */
    public int getTotalDetections() {
        return enabled ? detections.get() : 0;
    }

    /** Get total punishments since plugin start. */
    public int getTotalPunishments() {
        return enabled ? punishments.get() : 0;
    }

    /** Get detection hit rate as percentage (punishments / detections). */
    public double getHitRate() {
        int det = getTotalDetections();
        int pun = getTotalPunishments();
        if (det == 0) return 0;
        return (double) pun / det * 100;
    }
}
