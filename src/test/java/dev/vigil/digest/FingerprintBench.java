package dev.vigil.digest;

import java.util.List;

/**
 * Microbenchmark for SqlFingerprinter normalization and hashing throughput.
 */
public class FingerprintBench {
  private static final List<String> QUERIES = List.of(
      "SELECT * FROM users WHERE id = 42 AND name = 'bob'",
      "select * from orders where total_cents > 45000 order by created_at desc limit 20",
      "select c.name, count(*) from customers c join orders o on o.customer_id = c.id where c.id in (12, 45, 99) group by c.name",
      "insert into audit_log (user_id, action, ts) values (101, 'login', now()), (102, 'logout', now())",
      "update accounts set balance = balance - 100.50, updated_at = '2026-01-01 00:00:00' where id = 55",
      "delete from sessions where expires_at < '2026-01-01' and user_id in (1, 2, 3, 4, 5)",
      "select `order_id`, `amount` from `billing`.`invoices` where status = \"PAID\" and tax_rate = 0.0825",
      "SELECT /*+ INDEX(t idx_val) */ id, val FROM items WHERE val BETWEEN 10 AND 500",
      "select count(distinct customer_id) from orders where status in ('PENDING', 'PROCESSING', 'SHIPPED')",
      "select sleep(0.07), id from customers where id = 420"
  );

  public static void main(String[] args) {
    SqlFingerprinter fp = new SqlFingerprinter(SqlFingerprinter.Dialect.MYSQL);

    System.out.println("Warming up with 200,000 iterations...");
    int qCount = QUERIES.size();
    for (int i = 0; i < 200_000; i++) {
      fp.fingerprint(QUERIES.get(i % qCount));
    }

    System.out.println("Running benchmark for 2,000,000 calls...");
    long start = System.nanoTime();
    for (int i = 0; i < 2_000_000; i++) {
      fp.fingerprint(QUERIES.get(i % qCount));
    }
    long elapsed = System.nanoTime() - start;

    double seconds = elapsed / 1_000_000_000.0;
    double opsPerSec = 2_000_000.0 / seconds;
    double microsPerOp = (elapsed / 1000.0) / 2_000_000.0;

    System.out.printf("Done: 2,000,000 operations in %.3f s%n", seconds);
    System.out.printf("Throughput: %,.0f fingerprints/sec%n", opsPerSec);
    System.out.printf("Latency: %.2f µs/op%n", microsPerOp);
  }
}
