package icu.epochcraft.antilitematica.util

/**
 * 令牌桶限速器：控制单位时间内的操作次数（防 Printer 放置限速用）。
 *
 * @author 阿清
 */
class TokenBucket(private val capacity: Int, private val refillPerSecond: Int) {

    private var tokens: Double = capacity.toDouble()
    private var lastRefill: Long = System.currentTimeMillis()

    /** 尝试消耗 [count] 个令牌，不足返回 false */
    @Synchronized
    fun tryConsume(count: Int): Boolean {
        refill()
        if (tokens >= count) {
            tokens -= count
            return true
        }
        return false
    }

    private fun refill() {
        val now = System.currentTimeMillis()
        val elapsedSeconds = (now - lastRefill) / 1000.0
        if (elapsedSeconds > 0) {
            tokens = minOf(capacity.toDouble(), tokens + elapsedSeconds * refillPerSecond)
            lastRefill = now
        }
    }

    companion object {
        /** 按每秒速率创建（容量 = 突发余量） */
        fun perSecond(rate: Int, capacity: Int = rate * 2): TokenBucket = TokenBucket(capacity, rate)
    }
}
