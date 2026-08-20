package com.bettercontent.heatsync.compat.fiahi;

import java.util.ArrayDeque;
import java.util.Deque;

public final class FiahiAmbientContext {
    private static final ThreadLocal<Deque<Double>> AMBIENT_TARGETS = new ThreadLocal<>();

    private FiahiAmbientContext() {
    }

    public static void pushMinecraftTemperature(final double temperature) {
        Deque<Double> targets = AMBIENT_TARGETS.get();
        if (targets == null) {
            targets = new ArrayDeque<>();
            AMBIENT_TARGETS.set(targets);
        }
        targets.push(FiahiHeatMath.ambientMinecraftToCelsius(temperature));
    }

    public static void pop() {
        final Deque<Double> targets = AMBIENT_TARGETS.get();
        if (targets != null && !targets.isEmpty()) {
            targets.pop();
        }
        if (targets == null || targets.isEmpty()) {
            AMBIENT_TARGETS.remove();
        }
    }

    public static double targetOr(final double fallback) {
        final Deque<Double> targets = AMBIENT_TARGETS.get();
        return targets == null || targets.isEmpty() ? fallback : targets.peek();
    }
}
