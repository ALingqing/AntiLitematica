package top.chenray.antilitematica.util;

public final class TokenBucket {
   private final double refillPerMillis;
   private final double capacity;
   private double tokens;
   private long lastRefillMs;

   private TokenBucket(double refillPerSecond, double capacity) {
      this.refillPerMillis = refillPerSecond / (double)1000.0F;
      this.capacity = Math.max((double)1.0F, capacity);
      this.tokens = this.capacity;
      this.lastRefillMs = System.currentTimeMillis();
   }

   public static TokenBucket perSecond(double rate, double capacity) {
      return new TokenBucket(rate, capacity);
   }

   public boolean tryConsume(int amount) {
      if (amount <= 0) return true;
      this.refill();
      if (this.tokens + 1.0E-9 < (double)amount) return false;
      this.tokens -= (double)amount;
      return true;
   }

   private void refill() {
      long now = System.currentTimeMillis();
      long delta = now - this.lastRefillMs;
      if (delta > 0L) {
         this.tokens = Math.min(this.capacity, this.tokens + (double)delta * this.refillPerMillis);
         this.lastRefillMs = now;
      }
   }
}
