package io.apizit.reference;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class Sleeper {
  public void sleep(Duration duration) throws InterruptedException {
    Thread.sleep(duration);
  }
}
