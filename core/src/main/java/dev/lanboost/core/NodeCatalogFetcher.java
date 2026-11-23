package dev.lanboost.core;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import javax.net.ssl.SSLException;
import okhttp3.*;

/** Bounded requests to two official endpoints, independent of the benchmark network. */
public final class NodeCatalogFetcher {
  public static final int MAX_BYTES = 1_048_576;
  private final Call.Factory calls;

  public NodeCatalogFetcher(Call.Factory calls) {
    this.calls = calls;
  }

  public static final class Result {
    public final List<Candidate> nodes;
    public final String source;

    public Result(List<Candidate> nodes, String source) {
      this.nodes = nodes;
      this.source = source;
    }
  }

  private static final class Failure extends IOException {
    final boolean retryable;

    Failure(String message, boolean retryable) {
      super(message);
      this.retryable = retryable;
    }
  }

  public Result fetch(Profile profile, Consumer<String> progress) throws IOException {
    String path = "IP/IP." + profile.listKey + ".txt";
    return fetch(
        HttpUrl.get("https://raw.githubusercontent.com/skydevil88/XboxDownload/master/" + path),
        HttpUrl.get(
            "https://api.github.com/repos/skydevil88/XboxDownload/contents/"
                + path
                + "?ref=master"),
        progress);
  }

  // Injectable endpoints allow offline protocol tests; the application uses the official URLs
  // above.
  public Result fetch(HttpUrl raw, HttpUrl api, Consumer<String> progress) throws IOException {
    List<String> errors = new ArrayList<>();
    HttpUrl[] urls = {raw, api};
    String[] names = {"GitHub Raw", "GitHub API"};
    int retry = -1;
    for (int i = 0; i < 3; i++) {
      if (i == 2 && retry < 0) break;
      int endpoint = i < 2 ? i : retry;
      String source = names[endpoint];
      progress.accept((i == 2 ? "重试 " : "正在获取 · ") + source);
      try {
        return new Result(request(urls[endpoint]), source);
      } catch (IOException error) {
        errors.add(source + (i == 2 ? " 重试" : "") + "：" + describe(error));
        if (!(error instanceof Failure) || ((Failure) error).retryable) retry = endpoint;
      }
    }
    throw new IOException(String.join("\n", errors));
  }

  private List<Candidate> request(HttpUrl url) throws IOException {
    Request request =
        new Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.raw+json")
            .header("User-Agent", "LanBoost")
            .build();
    try (Response response = calls.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        int code = response.code();
        String reason = code == 403 || code == 429 ? "（访问受限或请求频率限制，稍后重试）" : "";
        throw new Failure("HTTP " + code + reason, code >= 500 || code == 408);
      }
      if (response.body() == null) throw new Failure("空响应", false);
      if (response.body().contentLength() > MAX_BYTES) throw new Failure("节点文件超过 1 MiB", false);
      String type = response.header("Content-Type", "").toLowerCase(Locale.ROOT);
      if (type.contains("text/html") || type.contains("application/json"))
        throw new Failure("返回的不是节点文本", false);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (InputStream stream = response.body().byteStream()) {
        byte[] buffer = new byte[8192];
        int size;
        while ((size = stream.read(buffer)) != -1) {
          if (out.size() + size > MAX_BYTES) throw new Failure("节点文件超过 1 MiB", false);
          out.write(buffer, 0, size);
        }
      }
      return parse(out.toString(StandardCharsets.UTF_8.name()));
    }
  }

  static List<Candidate> parse(String text) throws IOException {
    text = text.replace("\uFEFF", "").trim();
    if (text.startsWith("<") || text.startsWith("{") || text.startsWith("["))
      throw new Failure("返回的不是节点文本", false);
    Map<String, Candidate> nodes = new LinkedHashMap<>();
    for (String line : text.split("\\R")) {
      String[] parts = line.trim().split("\\s+", 2);
      if (Ipv4.valid(parts[0])) {
        Candidate candidate = new Candidate(parts[0], parts.length > 1 ? parts[1] : "");
        candidate.origin = "catalog";
        nodes.putIfAbsent(candidate.ip, candidate);
        if (nodes.size() > 2048) throw new Failure("节点库超过 2048 项，未覆盖原有列表", false);
      }
    }
    if (nodes.isEmpty()) throw new Failure("节点文件中没有有效 IPv4", false);
    return new ArrayList<>(nodes.values());
  }

  public static String describe(IOException error) {
    if (error instanceof Failure) return error.getMessage();
    if (error instanceof UnknownHostException) return "域名解析失败";
    if (error instanceof InterruptedIOException) return "连接或读取超时";
    if (error instanceof SSLException) return "TLS 连接或证书校验失败";
    if (error instanceof ConnectException) return "连接失败";
    return "网络连接中断（" + error.getClass().getSimpleName() + "）";
  }
}
