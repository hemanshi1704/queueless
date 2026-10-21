package com.queueless.service;

/**
 * Pure maths for wait-time estimates, kept free of Spring so it is trivial to unit test.
 *
 * The average service time is an exponential moving average (EMA): every finished customer
 * nudges the estimate toward what actually happened, so ETAs adapt to each shop's real pace.
 */
public final class EtaCalculator {

    static final double ALPHA = 0.3;
    static final double MIN_SECONDS = 30;
    static final double MAX_SECONDS = 3600;

    private EtaCalculator() {
    }

    public static double updateAverage(double currentAvgSeconds, double observedSeconds) {
        double observed = clamp(observedSeconds);
        return clamp(ALPHA * observed + (1 - ALPHA) * currentAvgSeconds);
    }

    /**
     * @param peopleAhead      waiting customers who will be served before this one
     * @param avgSeconds       current average service time
     * @param elapsedOnCurrent seconds the customer being served has already used, or null if nobody is
     */
    public static long etaSeconds(int peopleAhead, double avgSeconds, Long elapsedOnCurrent) {
        double remainingOnCurrent = elapsedOnCurrent == null ? 0 : Math.max(0, avgSeconds - elapsedOnCurrent);
        return Math.round(peopleAhead * avgSeconds + remainingOnCurrent);
    }

    private static double clamp(double v) {
        return Math.min(MAX_SECONDS, Math.max(MIN_SECONDS, v));
    }
}
