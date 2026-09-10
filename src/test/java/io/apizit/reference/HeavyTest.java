package io.apizit.reference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HeavyTest {
  @Autowired MockMvc mvc;

  @Test
  void healthDoesNotInitializeFeatures() {
    FeatureService fresh = new FeatureService();
    assertFalse(fresh.isInitialized());
    assertEquals("ok", new ReferenceController(new Sleeper()).health().get("status"));
    assertFalse(fresh.isInitialized());
  }

  @Test
  void textAndReady() throws Exception {
    mvc.perform(
            post("/text/embedding").contentType("application/json").content("{\"text\":\"hello\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dimension").value(256))
        .andExpect(jsonPath("$.embedding.length()").value(256));
    mvc.perform(
            post("/text/similarity")
                .contentType("application/json")
                .content("{\"left\":\"hello\",\"right\":\"hello\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.similarity").value(org.hamcrest.Matchers.closeTo(1, 1e-6)));
    mvc.perform(get("/ready"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ready"));
    mvc.perform(
            post("/text/embedding").contentType("application/json").content("{\"text\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void actualNativeImageProcessing() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    BufferedImage image = new BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB);
    ImageIO.write(image, "PNG", bytes);
    MockMultipartFile file =
        new MockMultipartFile("file", "sample.png", "image/png", bytes.toByteArray());
    mvc.perform(multipart("/image/analyze").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.image.width").value(24))
        .andExpect(jsonPath("$.image.height").value(16))
        .andExpect(jsonPath("$.predictions.length()").value(3));
    mvc.perform(multipart("/image/embedding").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dimension").value(48))
        .andExpect(jsonPath("$.embedding.length()").value(48));
    mvc.perform(
            multipart("/image/analyze")
                .file(new MockMultipartFile("file", "bad.png", "image/png", new byte[] {1, 2})))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/image/analyze")
                .file(new MockMultipartFile("file", "bad.txt", "text/plain", new byte[] {1})))
        .andExpect(status().isUnsupportedMediaType());
    mvc.perform(
            multipart("/image/analyze")
                .file(
                    new MockMultipartFile(
                        "file", "big.png", "image/png", new byte[4 * 1024 * 1024 + 1])))
        .andExpect(status().isPayloadTooLarge());
    mvc.perform(multipart("/image/embedding")).andExpect(status().isBadRequest());
  }

  @Test
  void boundsConcurrentFeatureWork() {
    FeatureService service = new FeatureService();
    InvalidInput error =
        assertThrows(InvalidInput.class, () -> service.bounded(() -> service.bounded(() -> true)));
    assertEquals(429, error.status());
    assertTrue(service.bounded(() -> true));
  }
}
