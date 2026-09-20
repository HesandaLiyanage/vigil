package dev.vigil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Invariant: Bootstraps the Vigil watchdog application with configuration binding and scheduling enabled.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class VigilApplication {
  public static void main(String[] args) {
    SpringApplication.run(VigilApplication.class, args);
  }
}
