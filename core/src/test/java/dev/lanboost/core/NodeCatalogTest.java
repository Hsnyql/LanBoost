package dev.lanboost.core;

import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.Test;

public class NodeCatalogTest {
  private OkHttpClient client() {
    return new OkHttpClient.Builder()
        .callTimeout(1, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build();
  }

  @Test
  public void rawFailureUsesApiAndRequestsRawRepresentation() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(404));
      server.enqueue(
          new MockResponse()
              .setBody("XboxCn1\n192.0.2.10 Region ISP\n192.0.2.10 duplicate\n192.0.2.11\n"));
      NodeCatalogFetcher.Result result =
          new NodeCatalogFetcher(client()).fetch(server.url("/raw"), server.url("/api"), s -> {});
      assertEquals("GitHub API", result.source);
      assertEquals(2, result.nodes.size());
      assertEquals("Region ISP", result.nodes.get(0).label);
      assertEquals("catalog", result.nodes.get(0).origin);
      assertEquals("/raw", server.takeRequest().getPath());
      RecordedRequest api = server.takeRequest();
      assertEquals("/api", api.getPath());
      assertEquals("application/vnd.github.raw+json", api.getHeader("Accept"));
      assertEquals(2, server.getRequestCount());
    }
  }

  @Test
  public void transientFailuresHaveOneBoundedRetry() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(503));
      server.enqueue(new MockResponse().setResponseCode(502));
      server.enqueue(new MockResponse().setBody("192.0.2.9 example\n"));
      NodeCatalogFetcher.Result result =
          new NodeCatalogFetcher(client()).fetch(server.url("/raw"), server.url("/api"), s -> {});
      assertEquals("GitHub API", result.source);
      assertEquals(3, server.getRequestCount());
    }
  }

  @Test
  public void invalidOrRateLimitedResponsesDoNotBecomeCatalogs() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setBody("<html>\n192.0.2.7\n</html>"));
      server.enqueue(new MockResponse().setResponseCode(429));
      IOException error =
          assertThrows(
              IOException.class,
              () ->
                  new NodeCatalogFetcher(client())
                      .fetch(server.url("/raw"), server.url("/api"), s -> {}));
      assertTrue(error.getMessage().contains("不是节点文本"));
      assertTrue(error.getMessage().contains("429"));
      assertEquals(2, server.getRequestCount());
    }
  }

  @Test
  public void oversizedAndEmptyBodiesAreRejected() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse().setBody("192.0.2.10\n" + "x".repeat(NodeCatalogFetcher.MAX_BYTES)));
      server.enqueue(new MockResponse().setBody("upstream unavailable"));
      IOException error =
          assertThrows(
              IOException.class,
              () ->
                  new NodeCatalogFetcher(client())
                      .fetch(server.url("/raw"), server.url("/api"), s -> {}));
      assertTrue(error.getMessage().contains("1 MiB"));
      assertTrue(error.getMessage().contains("没有有效 IPv4"));
    }
  }

  @Test
  public void slowEndpointFallsBackWithoutUnboundedWait() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setBody("192.0.2.1\n"));
      server.enqueue(new MockResponse().setBody("192.0.2.2\n"));
      NodeCatalogFetcher.Result result =
          new NodeCatalogFetcher(client()).fetch(server.url("/raw"), server.url("/api"), s -> {});
      assertEquals("192.0.2.2", result.nodes.get(0).ip);
      assertEquals(2, server.getRequestCount());
    }
  }

  @Test
  public void explainsNetworkFailuresWithoutLeakingArbitraryExceptionText() {
    assertEquals("域名解析失败", NodeCatalogFetcher.describe(new UnknownHostException("private-host")));
    assertEquals(
        "连接或读取超时", NodeCatalogFetcher.describe(new SocketTimeoutException("private-path")));
    assertEquals("连接失败", NodeCatalogFetcher.describe(new ConnectException()));
    assertEquals("TLS 连接或证书校验失败", NodeCatalogFetcher.describe(new SSLException("secret")));
  }

  @Test
  public void oversizedCatalogCannotBeSilentlyTruncated() {
    StringBuilder data = new StringBuilder();
    for (int i = 0; i < 2049; i++)
      data.append("198.18.").append(i / 256).append('.').append(i % 256).append('\n');
    assertThrows(IOException.class, () -> NodeCatalogFetcher.parse(data.toString()));
  }

  @Test
  public void refreshRetainsResultsSelectionAndImportedNodes() {
    Candidate measured = new Candidate("192.0.2.1", "old");
    measured.mbps = 123;
    measured.bytes = 1234;
    measured.latencyMs = 4;
    measured.measuredAt = 100;
    measured.throughputAttempted = true;
    measured.testedUrl = "http://example.com/test";
    measured.origin = "catalog";
    Candidate selected = new Candidate("192.0.2.2", "selected");
    selected.origin = "catalog";
    Candidate stale = new Candidate("192.0.2.3", "obsolete");
    stale.origin = "catalog";
    Candidate manual = new Candidate("192.0.2.4", "manual");
    List<Candidate> merged =
        NodeLists.mergeCatalog(
            Arrays.asList(measured, selected, stale, manual),
            Arrays.asList(
                new Candidate("192.0.2.1", "new region"), new Candidate("192.0.2.5", "new")),
            selected.ip);
    assertEquals(4, merged.size());
    Candidate first = merged.get(0);
    assertEquals("new region", first.label);
    assertEquals(123, first.mbps, 0);
    assertEquals(1234, first.bytes);
    assertEquals(100, first.measuredAt);
    assertTrue(first.throughputAttempted);
    assertEquals("saved", merged.get(2).origin);
    assertEquals(selected.ip, merged.get(2).ip);
    assertEquals("old", measured.label); // merge does not mutate inputs
    assertEquals(merged.size(), NodeLists.add(merged, Arrays.asList(measured), "dns").size());
    assertEquals(3, NodeLists.pending(merged, "http://example.com/test").size());
    assertEquals(4, NodeLists.pending(merged, "http://example.com/changed").size());
  }

  @Test
  public void removedCatalogNodesRetainLatencyAndFailureRecords() {
    Candidate latency = new Candidate("192.0.2.1", "old latency node");
    latency.origin = "catalog";
    latency.latencyMs = 7;
    latency.status = "连通 · 仅测延迟";
    Candidate failed = new Candidate("192.0.2.2", "old failed node");
    failed.origin = "catalog";
    failed.status = "连接失败";
    List<Candidate> merged =
        NodeLists.mergeCatalog(
            Arrays.asList(latency, failed), Arrays.asList(new Candidate("192.0.2.3", "new")), "");
    assertEquals(3, merged.size());
    assertEquals(7, merged.get(1).latencyMs);
    assertEquals("连接失败", merged.get(2).status);
    assertEquals("saved", merged.get(1).origin);
    assertEquals("saved", merged.get(2).origin);
  }
}
