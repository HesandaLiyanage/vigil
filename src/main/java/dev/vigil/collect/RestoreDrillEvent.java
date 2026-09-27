package dev.vigil.collect;

import jdk.jfr.Category;
import jdk.jfr.DataAmount;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * Invariant: JFR event capturing duration, outcome, and backup size of an automated restore verification drill.
 */
@Name("dev.vigil.RestoreDrill")
@Label("Vigil Restore Drill")
@Category({"Vigil", "Drill"})
@StackTrace(false)
public class RestoreDrillEvent extends Event {
  @Label("Target") public String target;
  @Label("Engine") public String engine;
  @Label("Status") public String status;
  @Label("Backup Bytes") @DataAmount public long backupBytes;
}
