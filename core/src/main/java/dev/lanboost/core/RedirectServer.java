package dev.lanboost.core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.apache.http.*;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.DefaultBHttpServerConnection;
import org.apache.http.message.BasicHttpResponse;

public final class RedirectServer implements AutoCloseable {
  private final Rules rules;
  private final Predicate<InetAddress> allow;
  private final Consumer<String> log;
  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16));
  private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
  private ServerSocket listener;
  private volatile boolean running;

  public RedirectServer(Rules rules, Predicate<InetAddress> allow, Consumer<String> log) {
    this.rules = rules;
    this.allow = allow;
    this.log = log;
  }

  public void start(InetAddress address, int port) throws IOException {
    listener = new ServerSocket();
    listener.bind(new InetSocketAddress(address, port), 16);
    running = true;
    new Thread(
            () -> {
              while (running)
                try {
                  Socket s = listener.accept();
                  s.setSoTimeout(3000);
                  if (!allow.test(s.getInetAddress())) {
                    s.close();
                    continue;
                  }
                  clients.add(s);
                  try {
                    workers.execute(() -> handle(s));
                  } catch (RejectedExecutionException e) {
                    clients.remove(s);
                    s.close();
                  }
                } catch (IOException e) {
                  if (running) log.accept("HTTP: " + e.getMessage());
                }
            },
            "http-redirect")
        .start();
  }

  public int port() {
    return listener.getLocalPort();
  }

  private void handle(Socket s) {
    try (Socket ignored = s;
        DefaultBHttpServerConnection conn =
            new DefaultBHttpServerConnection(
                8192,
                null,
                null,
                MessageConstraints.custom().setMaxHeaderCount(48).setMaxLineLength(8192).build())) {
      conn.bind(s);
      HttpRequest request = conn.receiveRequestHeader();
      String method = request.getRequestLine().getMethod();
      Header host = request.getFirstHeader("Host");
      String raw = request.getRequestLine().getUri();
      String hostname =
          host == null ? "" : host.getValue().toLowerCase(Locale.ROOT).replaceFirst(":80$", "");
      String target = rules.redirectHost(hostname);
      URI uri = new URI(raw);
      boolean valid =
          raw.startsWith("/")
              && !raw.startsWith("//")
              && uri.getRawAuthority() == null
              && uri.getRawFragment() == null
              && !raw.contains("\r")
              && !raw.contains("\n");
      int status =
          !method.equals("GET") && !method.equals("HEAD")
              ? 405
              : target == null || !valid ? 404 : 307;
      BasicHttpResponse response =
          new BasicHttpResponse(
              HttpVersion.HTTP_1_1, status, status == 307 ? "Temporary Redirect" : "Not Available");
      if (status == 307) response.setHeader("Location", "http://" + target + raw);
      response.setHeader("Content-Length", "0");
      response.setHeader("Connection", "close");
      response.setHeader("Cache-Control", "no-store");
      conn.sendResponseHeader(response);
      conn.flush();
      log.accept("HTTP " + s.getInetAddress().getHostAddress() + " " + hostname + " " + status);
    } catch (IOException | HttpException | URISyntaxException ignored) {
    } finally {
      clients.remove(s);
    }
  }

  public void close() {
    running = false;
    if (listener != null)
      try {
        listener.close();
      } catch (IOException ignored) {
      }
    for (Socket s : clients)
      try {
        s.close();
      } catch (IOException ignored) {
      }
    workers.shutdownNow();
  }
}
