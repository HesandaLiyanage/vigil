package dev.vigil.collect;

import dev.vigil.model.ManagedTarget;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Invariant: Dispatches tasks on virtual threads per target with an in-flight guard preventing pile-ups when targets stall.
 */
public final class PerTargetRunner implements AutoCloseable {
  @FunctionalInterface
  public interface Task {
    void run(ManagedTarget t) throws Exception;
  }

  private static final Logger log = LoggerFactory.getLogger(PerTargetRunner.class);

  private final String name;
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
  private final ScheduledExecutorService ticker;
  private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

  public PerTargetRunner(String name) {
    this.name = name;
    this.ticker = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("vigil-" + name).daemon().factory());
  }

  /**
   * Every tick, each target gets its own virtual thread. If the previous cycle for that
   * target is still running, the tick is skipped, so a hung database can never pile up work.
   */
  public void schedule(Duration every, Supplier<? extends Collection<ManagedTarget>> targets, Task task) {
    ticker.scheduleWithFixedDelay(() -> {
      for (ManagedTarget t : targets.get()) {
        if (!inFlight.add(name + ":" + t.id())) continue;
        workers.submit(() -> {
          try {
            task.run(t);
          } catch (Exception e) {
            log.warn("{} failed for {}: {}", name, t.id(), e.toString());
          } finally {
            inFlight.remove(name + ":" + t.id());
          }
        });
      }
    }, 0, every.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    ticker.shutdownNow();
    workers.shutdownNow();
  }
}
