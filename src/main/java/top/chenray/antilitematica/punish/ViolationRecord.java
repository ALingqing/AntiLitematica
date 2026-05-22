package top.chenray.antilitematica.punish;

import java.util.UUID;

/**
 * Data class for a player's violation record.
 */
public final class ViolationRecord {
   private final UUID uuid;
   private String playerName;
   private int count;
   private long firstViolation;
   private long lastViolation;
   private int totalViolations;

   public ViolationRecord(UUID uuid, String playerName, int count, long firstViolation, long lastViolation, int totalViolations) {
      this.uuid = uuid;
      this.playerName = playerName;
      this.count = count;
      this.firstViolation = firstViolation;
      this.lastViolation = lastViolation;
      this.totalViolations = totalViolations;
   }

   public UUID uuid() {
      return this.uuid;
   }

   public String playerName() {
      return this.playerName;
   }

   public void playerName(String playerName) {
      this.playerName = playerName;
   }

   public int count() {
      return this.count;
   }

   public void count(int count) {
      this.count = count;
   }

   public long firstViolation() {
      return this.firstViolation;
   }

   public void firstViolation(long firstViolation) {
      this.firstViolation = firstViolation;
   }

   public long lastViolation() {
      return this.lastViolation;
   }

   public void lastViolation(long lastViolation) {
      this.lastViolation = lastViolation;
   }

   public int totalViolations() {
      return this.totalViolations;
   }

   public void totalViolations(int totalViolations) {
      this.totalViolations = totalViolations;
   }
}
