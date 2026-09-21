package dev.vigil.collect;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * Invariant: JFR event capturing duration, outcome, and volume of a periodic target health collection cycle.
 */
@Name("dev.vigil.CollectionCycle")
@Label("Vigil Collection Cycle")
@Category({"Vigil", "Collection"})
@StackTrace(false)
public class CollectionCycleEvent extends Event {
  @Label("Target") public String target;
  @Label("Engine") public String engine;
  @Label("Success") public boolean success;
  @Label("Metrics Collected") public int metricCount;
}
