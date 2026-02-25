package com.example.gaitimu.sensor

class SampleRateEstimator(private val windowSize: Int = 20) {
    private val deltaQueue = ArrayDeque<Long>()
    private var previousTimestampNs: Long? = null

    fun reset() {
        deltaQueue.clear()
        previousTimestampNs = null
    }

    fun onSample(timestampNs: Long): Float {
        val previous = previousTimestampNs
        previousTimestampNs = timestampNs
        if (previous == null) {
            return 0f
        }

        val deltaNs = timestampNs - previous
        if (deltaNs <= 0L) {
            return currentHz()
        }

        deltaQueue.addLast(deltaNs)
        if (deltaQueue.size > windowSize) {
            deltaQueue.removeFirst()
        }
        return currentHz()
    }

    private fun currentHz(): Float {
        if (deltaQueue.isEmpty()) {
            return 0f
        }
        val averageDeltaNs = deltaQueue.average().toFloat()
        if (averageDeltaNs <= 0f) {
            return 0f
        }
        return 1_000_000_000f / averageDeltaNs
    }
}
