package dev.vigil.digest;

import java.util.concurrent.atomic.LongAdder;
import org.HdrHistogram.ConcurrentHistogram;

/**
 * Invariant: Thread-safe latency tracking per SQL fingerprint without lock contention on writes.
 */
public class FingerprintStats {
  private final SqlFingerprinter.Fingerprint fp;
  private final String example;
  private final LongAdder count = new LongAdder();
  private final LongAdder totalMicros = new LongAdder();
  // 1 microsecond to 1 hour, 3 significant digits:
  private final ConcurrentHistogram hist = new ConcurrentHistogram(1L, 3600_000_000L, 3);

  public FingerprintStats(SqlFingerprinter.Fingerprint fp, String example) {
    this.fp = fp;
    this.example = example.length() > 256 ? example.substring(0, 256) : example;
  }

  public void record(long micros, long times) {
    count.add(times);
    totalMicros.add(micros * times);
    long capped = Math.max(1L, Math.min(micros, 3600_000_000L));
    for (int i = 0; i < Math.min(times, 1000); i++) {
      hist.recordValue(capped);
    }
  }

  public long count() {
    return count.sum();
  }

  public long totalMicros() {
    return totalMicros.sum();
  }

  public record View(
      String id,
      String fingerprint,
      String example,
      long count,
      double totalMs,
      double sharePct,
      double p50Ms,
      double p95Ms,
      double p99Ms,
      double maxMs) {}

  public View view(double grandTotalMicros) {
    return new View(
        fp.id(),
        fp.text(),
        example,
        count.sum(),
        totalMicros.sum() / 1000.0,
        grandTotalMicros == 0 ? 0 : 100.0 * totalMicros.sum() / grandTotalMicros,
        hist.getValueAtPercentile(50) / 1000.0,
        hist.getValueAtPercentile(95) / 1000.0,
        hist.getValueAtPercentile(99) / 1000.0,
        hist.getMaxValue() / 1000.0);
  }
}
