package io.apizit.reference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {
  @Autowired MockMvc mvc;
  @Autowired RequestMappingHandlerMapping mappings;
  @MockitoBean Sleeper sleeper;

  @Test
  void healthAndInfo() throws Exception {
    mvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"ok\"}"));
    mvc.perform(get("/info"))
        .andExpect(jsonPath("$.framework").value("springboot"))
        .andExpect(jsonPath("$.profile").value("heavy"));
    verifyNoInteractions(sleeper);
  }

  @Test
  void echoRoundTrip() throws Exception {
    mvc.perform(
            post("/echo")
                .contentType("application/json")
                .content("{\"message\":\"hello\",\"count\":2}"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"received\":{\"message\":\"hello\",\"count\":2}}"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "[]",
        "{}",
        "{",
        "{\"message\":\"\",\"count\":1}",
        "{\"message\":\"x\",\"count\":true}",
        "{\"message\":\"x\",\"count\":1.5}"
      })
  void invalidEcho(String body) throws Exception {
    mvc.perform(post("/echo").contentType("application/json").content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void itemContract() throws Exception {
    mvc.perform(get("/items/7"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"item_id\":7,\"include_details\":false}"))
        .andExpect(jsonPath("$.details").doesNotExist());
    mvc.perform(get("/items/7?include_details=true"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json("{\"item_id\":7,\"include_details\":true,\"details\":\"Reference item 7\"}"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/items/0", "/items/-1", "/items/abc", "/items/7?include_details=maybe"})
  void invalidItems(String path) throws Exception {
    mvc.perform(get(path)).andExpect(status().isBadRequest());
  }

  @Test
  void slowDuration() throws Exception {
    mvc.perform(get("/slow"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"delay_seconds\":80,\"status\":\"completed\"}"));
    verify(sleeper).sleep(Duration.ofSeconds(80));
  }

  @Test
  void exactRoutes() {
    Set<String> routes =
        mappings.getHandlerMethods().entrySet().stream()
            .filter(
                entry ->
                    entry.getValue().getBeanType().getPackageName().equals("io.apizit.reference"))
            .flatMap(entry -> entry.getKey().getPatternValues().stream())
            .collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "/health",
            "/info",
            "/echo",
            "/items/{item_id}",
            "/slow",
            "/ready",
            "/text/embedding",
            "/text/similarity",
            "/image/analyze",
            "/image/embedding"),
        routes);
  }
}
