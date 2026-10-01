package dev.vigil.drill;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;

/**
 * Invariant: Injects controlled corruptions into backup artifacts to prove verification failure detection.
 */
public final class FaultInjector {
  private FaultInjector() {}

  public static void apply(Path file, CorruptionMode mode) throws IOException {
    switch (mode) {
      case NONE -> {}
      case TRUNCATE -> {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE)) {
          ch.truncate((long) (ch.size() * 0.55));
        }
      }
      case BITROT -> {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
          byte[] junk = new byte[64];
          new Random(42).nextBytes(junk);
          raf.seek(raf.length() / 2);
          raf.write(junk);
        }
      }
    }
  }
}
