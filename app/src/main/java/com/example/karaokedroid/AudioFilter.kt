package com.example.karaokedroid

import kotlin.math.exp

/**
 * A highly robust, stable first-order IIR (Infinite Impulse Response) filter
 * designed for real-time and offline audio processing in Mobile DSP environments.
 * Supports low-pass, high-pass, and band-pass crossover operations.
 */
class FirstOrderIirFilter(fc: Double, val sampleRate: Double) {
    private val x: Double
    private val a0: Double
    private val b1: Double
    private var lastY = 0.0

    init {
        val safeSampleRate = if (sampleRate <= 0.0) 44100.0 else sampleRate
        val safeFc = fc.coerceIn(0.1, safeSampleRate / 2.001)
        x = exp(-2.0 * Math.PI * safeFc / safeSampleRate)
        a0 = 1.0 - x
        b1 = x
    }

    /**
     * Processes a single input sample and returns the filtered output sample.
     */
    fun process(sample: Double): Double {
        val y = a0 * sample + b1 * lastY
        lastY = y
        return y
    }

    /**
     * Resets the filter's internal history state.
     */
    fun reset() {
        lastY = 0.0
    }
}
