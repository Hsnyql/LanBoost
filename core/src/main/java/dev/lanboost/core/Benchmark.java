package dev.lanboost.core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.SocketFactory;
import okhttp3.*;

public final class Benchmark {
  private final Set<Call> active = ConcurrentHashMap.newKeySet();
  private volatile boolean cancelled;
  private final SocketFactory sockets;

  public Benchmark(SocketFactory sockets) {
    this.sockets = sockets;
  }

  public void cancel() {
    cancelled = true;
    for (Call call : active) call.cancel();
  }

  public boolean cancelled() {
    return cancelled;
  }

  public long latency(String ip, int port) {
    if (cancelled) return -1;
    try (Socket socket = sockets.createSocket()) {
      long start = System.nanoTime();
      socket.connect(new InetSocketAddress(Ipv4.address(ip), port), 1600);
      return Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    } catch (IOException e) {
      return -1;
    }
  }

  public Candidate measure(Candidate input, String url, int seconds, long limit) {
    Candidate result = input.copy();
    result.bytes = 0;
    result.mbps = 0;
    if (cancelled) {
      result.status = "已取消";
      return result;
    }
    HttpUrl parsed = HttpUrl.get(url);
    OkHttpClient client =
        new OkHttpClient.Builder()
            .socketFactory(sockets)
            .proxy(Proxy.NO_PROXY)
            .dns(
                host -> {
                  if (!host.equals(parsed.host())) throw new UnknownHostException(host);
                  return Collections.singletonList(Ipv4.address(input.ip));
                })
            .connectionPool(new ConnectionPool(0, 1, TimeUnit.SECONDS))
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2000, TimeUnit.MILLISECONDS)
            .callTimeout(seconds + 3L, TimeUnit.SECONDS)
            .build();
    Request request =
        new Request.Builder()
            .url(parsed)
            .header("Range", "bytes=0-" + (limit - 1))
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "LanBoost/0.1")
            .build();
    Call call = client.newCall(request);
    active.add(call);
    if (cancelled) call.cancel();
    long start = System.nanoTime(), end = start;
    boolean timed = false;
    try (Response response = call.execute()) {
      result.latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      if (response.code() != 200 && response.code() != 206)
        throw new IOException("HTTP " + response.code());
      String type = response.header("Content-Type", "").toLowerCase(Locale.ROOT);
      if (type.contains("text/html") || type.contains("application/json"))
        throw new IOException("非下载内容: " + type);
      if (response.body() == null) throw new IOException("空响应");
      try (InputStream stream = response.body().byteStream()) {
        byte[] buffer = new byte[65536];
        while (!cancelled && result.bytes < limit) {
          if (System.nanoTime() - start >= TimeUnit.SECONDS.toNanos(seconds)) {
            timed = true;
            break;
          }
          int count = stream.read(buffer, 0, (int) Math.min(buffer.length, limit - result.bytes));
          if (count < 0) break;
          result.bytes += count;
          end = System.nanoTime();
        }
      }
      result.status = cancelled ? "已取消" : result.bytes < 65536 ? "样本过小" : timed ? "完成 / 限时" : "完成";
    } catch (IOException e) {
      end = System.nanoTime();
      result.status =
          cancelled
              ? "已取消"
              : "失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      // Timeouts after sustained transfer remain useful bounded samples.
      if (!cancelled && result.bytes >= 65536 && e instanceof InterruptedIOException)
        result.status = "完成 / 超时样本";
    } finally {
      active.remove(call);
      client.connectionPool().evictAll();
      client.dispatcher().executorService().shutdown();
    }
    if (result.status.startsWith("完成"))
      result.mbps = result.bytes * 8.0 / Math.max(0.001, (end - start) / 1e9) / 1_000_000;
    return result;
  }
}
