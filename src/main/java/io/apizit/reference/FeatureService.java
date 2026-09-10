package io.apizit.reference;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.commons.math3.linear.*;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FeatureService {
  private final Semaphore slot = new Semaphore(1);
  private volatile RealMatrix projection;
  private volatile boolean imageReady;

  public boolean isInitialized() {
    return projection != null || imageReady;
  }

  public <T> T bounded(Supplier<T> operation) {
    if (!slot.tryAcquire()) throw new InvalidInput("One feature request is already running.", 429);
    try {
      return operation.get();
    } finally {
      slot.release();
    }
  }

  private synchronized RealMatrix projection() {
    if (projection == null) {
      Random random = new Random(44);
      double[][] seed = new double[256][256];
      for (double[] row : seed) for (int i = 0; i < row.length; i++) row[i] = random.nextGaussian();
      projection = new SingularValueDecomposition(new Array2DRowRealMatrix(seed)).getU();
    }
    return projection;
  }

  private synchronized void initializeImage() {
    if (!imageReady) {
      try (Mat probe = new Mat(1, 1, CV_8UC3)) {
        imageReady = !probe.empty();
      }
    }
  }

  public Map<String, Object> ready() {
    projection();
    initializeImage();
    return Map.of(
        "status", "ready", "models", Map.of("text", true, "image", true), "device", "cpu");
  }

  private double[] textVector(String text) {
    double[] features = new double[256];
    for (int i = 0; i < text.length(); i++)
      features[Math.floorMod(text.charAt(i) * 31 + (i > 0 ? text.charAt(i - 1) : 0), 256)]++;
    RealVector vector = projection().operate(new ArrayRealVector(features));
    return vector.getNorm() == 0 ? vector.toArray() : vector.unitVector().toArray();
  }

  public Map<String, Object> textEmbedding(String text) {
    return vector("commons-math-svd-character-v1", textVector(text));
  }

  public Map<String, Object> similarity(String left, String right) {
    double score =
        new ArrayRealVector(textVector(left)).dotProduct(new ArrayRealVector(textVector(right)));
    return Map.of(
        "model", "commons-math-svd-character-v1", "similarity", Math.max(-1, Math.min(1, score)));
  }

  private static Map<String, Object> vector(String model, double[] data) {
    return Map.of("model", model, "dimension", data.length, "embedding", data);
  }

  private record ImageFeatures(int width, int height, String format, double[] histogram) {}

  private ImageFeatures image(MultipartFile file) {
    if (file == null || file.isEmpty())
      throw new InvalidInput("Multipart field 'file' is required.");
    if (file.getSize() > 4 * 1024 * 1024) throw new InvalidInput("Image exceeds 4 MiB.", 413);
    if (!Set.of("image/png", "image/jpeg").contains(Objects.toString(file.getContentType(), "")))
      throw new InvalidInput("Only PNG and JPEG are supported.", 415);
    try (ImageInputStream input =
        ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new InvalidInput("Invalid image.");
      ImageReader reader = readers.next();
      BufferedImage decoded;
      String format;
      try {
        reader.setInput(input, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width <= 0 || height <= 0 || (long) width * height > 20000000)
          throw new InvalidInput("Image dimensions exceed the limit.", 413);
        format = reader.getFormatName().toUpperCase(Locale.ROOT);
        if (!Set.of("PNG", "JPEG").contains(format))
          throw new InvalidInput("Unsupported encoded image format.", 415);
        decoded = reader.read(0);
      } finally {
        reader.dispose();
      }
      initializeImage();
      int width = decoded.getWidth(), height = decoded.getHeight();
      double[] histogram = new double[48];
      try (Mat pixels = new Mat(height, width, CV_8UC3);
          Mat resized = new Mat();
          Size size = new Size(128, 128)) {
        try (UByteIndexer index = pixels.createIndexer()) {
          for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
              int rgb = decoded.getRGB(x, y);
              for (int c = 0; c < 3; c++) index.put(y, x, c, (rgb >> (16 - c * 8)) & 255);
            }
        }
        resize(pixels, resized, size, 0, 0, INTER_AREA);
        try (UByteIndexer index = resized.createIndexer()) {
          for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++)
              for (int c = 0; c < 3; c++) histogram[c * 16 + index.get(y, x, c) / 16]++;
        }
      }
      double norm = new ArrayRealVector(histogram).getNorm();
      for (int i = 0; i < histogram.length; i++) histogram[i] /= norm;
      return new ImageFeatures(width, height, format, histogram);
    } catch (IOException error) {
      throw new InvalidInput("Invalid image.");
    }
  }

  public Map<String, Object> imageEmbedding(MultipartFile file) {
    return vector("opencv-color-histogram-v1", image(file).histogram());
  }

  public Map<String, Object> analyze(MultipartFile file) {
    ImageFeatures image = image(file);
    double[] means = new double[3];
    for (int c = 0; c < 3; c++)
      for (int i = 0; i < 16; i++) means[c] += image.histogram()[c * 16 + i] * (i + 0.5);
    double sum = Arrays.stream(means).sum();
    List<Map<String, Object>> predictions = new ArrayList<>();
    String[] labels = {"red-channel", "green-channel", "blue-channel"};
    for (int c = 0; c < 3; c++)
      predictions.add(Map.of("label", labels[c], "score", sum == 0 ? 0 : means[c] / sum));
    return Map.of(
        "model",
        "opencv-color-histogram-v1",
        "image",
        Map.of("width", image.width(), "height", image.height(), "format", image.format()),
        "predictions",
        predictions);
  }
}
