package top.chenray.antilitematica.util;

public final class ViolationWindow {
   private final long windowMs;
   private int count;
   private long windowStartMs;

   public ViolationWindow(long windowMs) {
      this.windowMs = Math.max(1L, windowMs);
      this.windowStartMs = System.currentTimeMillis();
      this.count = 0;
   }

   public synchronized int addViolation() {
      long now = System.currentTimeMillis();
      if (now - this.windowStartMs > this.windowMs) {
         this.windowStartMs = now;
         this.count = 0;
      }

      ++this.count;
      return this.count;
   }
}
