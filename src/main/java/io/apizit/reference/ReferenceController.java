package io.apizit.reference;

import java.math.BigInteger;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReferenceController {
  private final Sleeper sleeper;

  public ReferenceController(Sleeper sleeper) {
    this.sleeper = sleeper;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of("status", "ok");
  }

  @GetMapping("/info")
  public Map<String, Object> info() {
    return Map.of("framework", "springboot", "profile", "heavy");
  }

  static String text(Map<String, Object> body, String field) {
    Object value = body == null ? null : body.get(field);
    if (!(value instanceof String text) || text.isBlank() || text.length() > 5000)
      throw new InvalidInput("Expected nonempty text of at most 5000 characters.");
    return text;
  }

  @PostMapping("/echo")
  public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
    String message = text(body, "message");
    Object count = body.get("count");
    if (!(count instanceof Integer || count instanceof Long || count instanceof BigInteger))
      throw new InvalidInput("'count' must be an integer.");
    return Map.of("received", Map.of("message", message, "count", count));
  }

  @GetMapping("/items/{item_id}")
  public Map<String, Object> item(
      @PathVariable("item_id") long itemId,
      @RequestParam(name = "include_details", defaultValue = "false") String rawDetails) {
    if (itemId < 1
        || !(rawDetails.equalsIgnoreCase("true") || rawDetails.equalsIgnoreCase("false")))
      throw new InvalidInput("Expected positive item ID and true/false include_details.");
    boolean details = Boolean.parseBoolean(rawDetails);
    Map<String, Object> result =
        new HashMap<>(Map.of("item_id", itemId, "include_details", details));
    if (details) result.put("details", "Reference item " + itemId);
    return result;
  }

  @GetMapping("/slow")
  public Map<String, Object> slow() throws InterruptedException {
    sleeper.sleep(Duration.ofSeconds(80));
    return Map.of("delay_seconds", 80, "status", "completed");
  }
}
