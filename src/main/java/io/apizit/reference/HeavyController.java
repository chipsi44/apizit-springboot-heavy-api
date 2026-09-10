package io.apizit.reference;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class HeavyController {
  private final FeatureService service;

  public HeavyController(FeatureService service) {
    this.service = service;
  }

  @GetMapping("/ready")
  public ResponseEntity<?> ready() {
    try {
      return ResponseEntity.ok(service.bounded(service::ready));
    } catch (InvalidInput error) {
      throw error;
    } catch (RuntimeException | LinkageError error) {
      return ResponseEntity.status(503)
          .body(
              Map.of(
                  "status",
                  "unavailable",
                  "models",
                  Map.of("text", false, "image", false),
                  "device",
                  "cpu"));
    }
  }

  @PostMapping("/text/embedding")
  public Map<String, Object> embedding(@RequestBody Map<String, Object> body) {
    String text = ReferenceController.text(body, "text");
    return service.bounded(() -> service.textEmbedding(text));
  }

  @PostMapping("/text/similarity")
  public Map<String, Object> similarity(@RequestBody Map<String, Object> body) {
    String left = ReferenceController.text(body, "left"),
        right = ReferenceController.text(body, "right");
    return service.bounded(() -> service.similarity(left, right));
  }

  @PostMapping("/image/analyze")
  public Map<String, Object> analyze(@RequestParam("file") MultipartFile file) {
    return service.bounded(() -> service.analyze(file));
  }

  @PostMapping("/image/embedding")
  public Map<String, Object> imageEmbedding(@RequestParam("file") MultipartFile file) {
    return service.bounded(() -> service.imageEmbedding(file));
  }
}
