package dev.lanboost.core;

import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.SocketFactory;
import okhttp3.mockwebserver.*;
import okio.Buffer;
import org.junit.Test;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public class NetworkTest {
  private Rules rules(boolean redirect) {
    return new Rules(
        "192.0.2.11",
        "192.0.2.12",
        "192.0.2.13",
        "192.0.2.105",
        redirect,
        true,
        Collections.emptyMap());
  }

  private Message query(String name, int type) throws Exception {
    return Message.newQuery(Record.newRecord(Name.fromString(name + "."), type, DClass.IN));
  }

  private Message mapped(DnsEngine engine, String name, int type) throws Exception {
    return new Message(engine.answer(query(name, type).toWire(), false));
  }

  @Test
  public void separateGroupsAndExactMatches() {
    Rules r = rules(false);
    assertEquals("192.0.2.11", r.ipFor("ASSETS1.XBOXLIVE.CN."));
    assertEquals("192.0.2.12", r.ipFor("dlassets.xboxlive.cn"));
    assertEquals("192.0.2.13", r.ipFor("assets1.xboxlive.com"));
    assertNull(r.ipFor("evil.assets1.xboxlive.cn"));
    assertNull(r.ipFor("login.live.com"));
    assertEquals("192.0.2.105", rules(true).ipFor("5.xb.delivery.microsoft.com"));
  }

  @Test
  public void applicationGroupIsIndependentAndUsesExactHosts() throws Exception {
    Rules selected =
        new Rules(
            "192.0.2.11",
            "192.0.2.12",
            "192.0.2.13",
            "192.0.2.14",
            "192.0.2.105",
            true,
            true,
            Collections.emptyMap());
    AtomicInteger forwarded = new AtomicInteger();
    DnsEngine engine =
        new DnsEngine(
            selected,
            q -> {
              forwarded.incrementAndGet();
              return DnsEngine.reply(q, Rcode.NOERROR);
            });
    for (String host :
        new String[] {
          "dl.delivery.mp.microsoft.com",
          "tlu.dl.delivery.mp.microsoft.com",
          "1d.tlu.dl.delivery.mp.microsoft.com"
        }) {
      assertEquals(Profile.APP, Rules.profileFor(host));
      assertEquals("192.0.2.14", selected.ipFor(host.toUpperCase(Locale.ROOT) + "."));
      assertNull(selected.redirectHost(host));
      Message answer = mapped(engine, host, Type.A);
      assertEquals(
          "192.0.2.14",
          ((ARecord) answer.getSection(Section.ANSWER).get(0)).getAddress().getHostAddress());
      for (int type : new int[] {Type.AAAA, Type.HTTPS, Type.SVCB})
        assertTrue(mapped(engine, host, type).getSection(Section.ANSWER).isEmpty());
    }
    assertEquals(0, forwarded.get());
    assertEquals("192.0.2.11", selected.ipFor("assets1.xboxlive.cn"));
    assertEquals("192.0.2.12", selected.ipFor("dlassets.xboxlive.cn"));
    for (String host : new String[] {"evil.dl.delivery.mp.microsoft.com", "login.live.com"}) {
      assertNull(selected.ipFor(host));
      mapped(engine, host, Type.AAAA);
    }
    assertEquals(2, forwarded.get());
    assertFalse(rules(false).suppressIpv6("dl.delivery.mp.microsoft.com"));
    assertNull(rules(false).ipFor("dl.delivery.mp.microsoft.com"));
  }

  @Test
  public void failoverRetriesTimeoutFailureAndInvalidAnswers() throws Exception {
    Message q = query("example.com", Type.A);
    List<DnsEngine.Upstream> broken =
        Arrays.asList(
            input -> {
              throw new SocketTimeoutException("timeout");
            },
            input -> DnsEngine.reply(input, Rcode.SERVFAIL),
            input -> DnsEngine.reply(input, Rcode.REFUSED),
            input -> {
              Message r = DnsEngine.reply(input, Rcode.NOERROR);
              r.getHeader().setID(input.getHeader().getID() ^ 1);
              return r;
            });
    for (DnsEngine.Upstream first : broken) {
      AtomicInteger calls = new AtomicInteger();
      FailoverUpstream fallback =
          new FailoverUpstream(
              first,
              input -> {
                calls.incrementAndGet();
                return DnsEngine.reply(input, Rcode.NOERROR);
              });
      assertEquals(Rcode.NOERROR, fallback.resolve(q).getRcode());
      assertEquals(1, calls.get());
    }
    assertThrows(
        IOException.class,
        () -> new FailoverUpstream(broken.toArray(new DnsEngine.Upstream[0])).resolve(q));
  }

  @Test
  public void failoverPreservesValidNegativeAnswersWithoutRetry() throws Exception {
    Message q = query("example.com", Type.A);
    for (int code : new int[] {Rcode.NOERROR, Rcode.NXDOMAIN}) {
      AtomicInteger calls = new AtomicInteger();
      Message answer =
          new FailoverUpstream(
                  input -> DnsEngine.reply(input, code),
                  input -> {
                    calls.incrementAndGet();
                    throw new IOException("must not run");
                  })
              .resolve(q);
      assertEquals(code, answer.getRcode());
      assertEquals(0, calls.get());
    }
  }

  @Test
  public void platformMappingsAreIndependentAndNeverUseXboxRedirects() throws Exception {
    Map<Profile, String> selections = new EnumMap<>(Profile.class);
    for (Profile profile : Profile.values())
      selections.put(profile, "192.0.2." + (20 + profile.ordinal()));
    Rules rules =
        new Rules(
            selections,
            EnumSet.allOf(Profile.class),
            "192.0.2.105",
            true,
            true,
            Collections.emptyMap());
    String[][] examples = {
      {"NS", "atum.hac.lp1.d4c.nintendo.net"}, {"NS", "ctest-dl-lp1.cdn.nintendo.net"},
      {"NS", "content.lp1.d4c.srv.nintendo.net"}, {"PS", "gst.prod.dl.playstation.net"},
      {"PS", "gs2.ww.prod.dl.playstation.net"}, {"PS", "ares.dl.playstation.net"},
      {"PS", "zeus.dl.playstation.net"}, {"EA", "origin-a.akamaihd.net"},
      {"BATTLE", "blzddist1-a.akamaihd.net"}, {"BATTLE", "downloader.battle.net"},
      {"AKAMAI", "cdn1.epicgames.com"}, {"AKAMAI", "download.cdn.ubi.com"},
      {"AKAMAI", "patch.dyn.riotcdn.net"}, {"AKAMAI", "test.akamaized.net"}
    };
    DnsEngine engine =
        new DnsEngine(
            rules,
            q -> {
              throw new IOException("must be mapped");
            });
    for (String[] example : examples) {
      Profile profile = Profile.valueOf(example[0]);
      String host = example[1];
      assertEquals(profile, Rules.profileFor(host));
      assertEquals(selections.get(profile), rules.ipFor(host.toUpperCase(Locale.ROOT) + "."));
      assertNull(rules.redirectHost(host));
      Message answer = mapped(engine, host, Type.A);
      assertEquals(
          selections.get(profile),
          ((ARecord) answer.getSection(Section.ANSWER).get(0)).getAddress().getHostAddress());
      assertTrue(mapped(engine, host, Type.AAAA).getSection(Section.ANSWER).isEmpty());
    }
  }

  @Test
  public void wildcardBoundariesSpecificGroupsAndCustomOverrides() {
    Map<Profile, String> picks = new EnumMap<>(Profile.class);
    picks.put(Profile.NS, "192.0.2.21");
    picks.put(Profile.AKAMAI, "192.0.2.22");
    Rules r =
        new Rules(
            picks,
            Collections.emptySet(),
            "",
            false,
            true,
            Rules.parseCustom("cdn1.epicgames.com 192.0.2.23"));
    assertEquals("192.0.2.21", r.ipFor("content.hac.lp1.d4c.nintendo.net"));
    assertEquals("192.0.2.22", r.ipFor("foo.akamaihd.net"));
    assertEquals("192.0.2.23", r.ipFor("cdn1.epicgames.com"));
    for (String host :
        new String[] {
          "badakamaihd.net",
          "akamaihd.net.evil.example",
          "cdn.ubi.com",
          "download.cdn.ubi.com.evil.example",
          "accounts.nintendo.com",
          "auth.api.sonyentertainmentnetwork.com",
          "assets1.xboxlive.cn",
          "origin-a.akamaihd.net",
          "blzddist1-a.akamaihd.net",
          "gst.prod.dl.playstation.net"
        }) {
      assertNull(host, r.ipFor(host));
      assertFalse(host, r.suppressIpv6(host));
    }
  }

  @Test
  public void fallbackBlocksOnlyApplyToEnabledGroupsAndCanBeDisabled() throws Exception {
    for (Profile p : new Profile[] {Profile.NS, Profile.EA, Profile.BATTLE}) {
      Map<Profile, String> picks = Collections.singletonMap(p, "192.0.2.21");
      Rules enabled = new Rules(picks, EnumSet.of(p), "", false, false, Collections.emptyMap());
      Rules disabled =
          new Rules(picks, Collections.emptySet(), "", false, false, Collections.emptyMap());
      Rules unselected =
          new Rules(Collections.emptyMap(), EnumSet.of(p), "", false, true, Collections.emptyMap());
      DnsEngine engine =
          new DnsEngine(
              enabled,
              q -> {
                throw new IOException("offline");
              });
      for (String host : Rules.blockedDomains(p)) {
        assertEquals("0.0.0.0", enabled.ipFor(host));
        assertNull(disabled.ipFor(host));
        assertNull(unselected.ipFor(host));
        assertFalse(disabled.suppressIpv6(host));
        for (int type : new int[] {Type.AAAA, Type.HTTPS, Type.SVCB}) {
          Message reply = mapped(engine, host, type);
          assertEquals(Rcode.NOERROR, reply.getRcode());
          assertTrue(reply.getSection(Section.ANSWER).isEmpty());
        }
      }
    }
  }

  @Test
  public void legacyNotRedirectedWithoutItsOwnWorkingNode() {
    Rules r =
        new Rules("192.0.2.1", "", "192.0.2.2", "192.168.1.2", true, true, Collections.emptyMap());
    assertNull(r.redirectHost("dlassets.xboxlive.com"));
    assertEquals("192.0.2.2", r.ipFor("dlassets.xboxlive.com"));
  }

  @Test
  public void customMappingAndValidation() {
    Rules r =
        new Rules(
            "",
            "",
            "",
            "",
            false,
            true,
            Rules.parseCustom("# example\nexample.com 192.0.2.3\nfoo.example=192.0.2.4"));
    assertEquals("192.0.2.3", r.ipFor("example.com"));
    assertNull(r.ipFor("sub.example.com"));
    assertThrows(
        IllegalArgumentException.class, () -> Rules.parseCustom("example.com 192.0.2.3;id"));
    assertFalse(Ipv4.valid("01.2.3.4"));
    assertFalse(Ipv4.valid("256.1.1.1"));
    assertTrue(Ipv4.sameSubnet("192.0.2.105", "192.0.2.7", 24));
    assertFalse(Ipv4.sameSubnet("192.0.2.105", "198.51.100.7", 24));
  }

  @Test
  public void mappedResponsesAndIpv6Isolation() throws Exception {
    AtomicInteger forwarded = new AtomicInteger();
    DnsEngine engine =
        new DnsEngine(
            rules(false),
            q -> {
              forwarded.incrementAndGet();
              return DnsEngine.reply(q, Rcode.NOERROR);
            });
    Message a = mapped(engine, "assets1.xboxlive.cn", Type.A);
    assertEquals(
        "192.0.2.11",
        ((ARecord) a.getSection(Section.ANSWER).get(0)).getAddress().getHostAddress());
    assertTrue(a.getHeader().getFlag(Flags.QR));
    assertFalse(a.getHeader().getFlag(Flags.AD));
    assertEquals(
        0, mapped(engine, "assets1.xboxlive.cn", Type.AAAA).getSection(Section.ANSWER).size());
    mapped(engine, "example.com", Type.AAAA);
    assertEquals(1, forwarded.get());
    mapped(engine, "_http._tcp.assets1.xboxlive.com", Type.SRV);
    assertEquals(2, forwarded.get());
  }

  @Test
  public void upstreamFailureAndMismatchedResponse() throws Exception {
    DnsEngine broken =
        new DnsEngine(
            rules(false),
            q -> {
              throw new IOException("offline");
            });
    assertEquals(Rcode.SERVFAIL, mapped(broken, "example.com", Type.A).getRcode());
    DnsEngine mismatch =
        new DnsEngine(
            rules(false),
            q -> {
              Message r = DnsEngine.reply(q, Rcode.NOERROR);
              r.getHeader().setID(q.getHeader().getID() ^ 1);
              return r;
            });
    assertEquals(Rcode.SERVFAIL, mapped(mismatch, "example.com", Type.A).getRcode());
    assertThrows(IOException.class, () -> broken.answer(new byte[] {0, 1, 2}, false));
  }

  @Test
  public void srvAdditionalRecordsUseSelectedCdn() throws Exception {
    DnsEngine engine =
        new DnsEngine(
            rules(true),
            q -> {
              Message r = DnsEngine.reply(q, Rcode.NOERROR);
              r.getHeader().setFlag(Flags.AD);
              Name target = Name.fromString("5.xb.delivery.microsoft.com.");
              r.addRecord(
                  new SRVRecord(q.getQuestion().getName(), DClass.IN, 300, 0, 0, 80, target),
                  Section.ANSWER);
              r.addRecord(
                  new ARecord(target, DClass.IN, 300, Ipv4.address("192.0.2.99")),
                  Section.ADDITIONAL);
              return r;
            });
    Message r = mapped(engine, "_http._tcp.assets1.xboxlive.com", Type.SRV);
    assertEquals(
        "192.0.2.105",
        ((ARecord) r.getSection(Section.ADDITIONAL).get(0)).getAddress().getHostAddress());
    assertFalse(r.getHeader().getFlag(Flags.AD));
  }

  @Test
  public void udpTruncationAndTcpFullResponse() throws Exception {
    DnsEngine engine =
        new DnsEngine(
            rules(false),
            q -> {
              Message r = DnsEngine.reply(q, Rcode.NOERROR);
              for (int i = 0; i < 60; i++)
                r.addRecord(
                    new TXTRecord(
                        q.getQuestion().getName(),
                        DClass.IN,
                        60,
                        "data-" + i + "-abcdefghijklmnopqrstuvwxyz"),
                    Section.ANSWER);
              return r;
            });
    byte[] q = query("example.com", Type.TXT).toWire();
    byte[] udp = engine.answer(q, false);
    assertTrue(udp.length <= 512);
    assertTrue(new Message(udp).getHeader().getFlag(Flags.TC));
    Message tcp = new Message(engine.answer(q, true));
    assertFalse(tcp.getHeader().getFlag(Flags.TC));
    assertEquals(60, tcp.getSection(Section.ANSWER).size());
  }

  private DnsServer startDnsFixture() throws IOException {
    // A UDP-assigned ephemeral port can already belong to TCP on a busy Linux runner.
    // Reserve a TCP port first, then bind both protocols. Retry only setup collisions, never
    // protocol assertions; closing the reservation leaves a small unavoidable allocation race.
    for (int attempt = 0; attempt < 8; attempt++) {
      int port;
      try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
        port = reservation.getLocalPort();
      }
      DnsServer server =
          new DnsServer(
              new DnsEngine(rules(false), q -> DnsEngine.reply(q, Rcode.NOERROR)),
              InetAddress::isLoopbackAddress,
              s -> {});
      try {
        server.start(InetAddress.getLoopbackAddress(), port);
        return server;
      } catch (BindException occupied) {
        server.close();
        if (attempt == 7) throw occupied;
      }
    }
    throw new AssertionError("Unreachable");
  }

  // TCP close is asynchronous; poll briefly, but keep SO_REUSEADDR off so TIME_WAIT still fails.
  private void assertPortReleased(int port) throws IOException, InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    BindException last = null;
    while (System.nanoTime() < deadline) {
      try (ServerSocket released = new ServerSocket()) {
        released.setReuseAddress(false);
        released.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1);
        assertEquals(port, released.getLocalPort());
        return;
      } catch (BindException e) {
        last = e;
        Thread.sleep(50);
      }
    }
    throw new AssertionError("Port " + port + " was not released after server close", last);
  }

  @Test
  public void udpAndTcpServerRoundTripAndClose() throws Exception {
    DnsServer server = startDnsFixture();
    int port = server.port();
    byte[] request = query("assets1.xboxlive.cn", Type.A).toWire();
    try (DatagramSocket udp = new DatagramSocket()) {
      udp.setSoTimeout(2000);
      udp.send(new DatagramPacket(request, request.length, InetAddress.getLoopbackAddress(), port));
      DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
      udp.receive(packet);
      assertEquals(
          1,
          new Message(Arrays.copyOf(packet.getData(), packet.getLength()))
              .getSection(Section.ANSWER)
              .size());
    }
    try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
      s.setSoTimeout(2000);
      DataOutputStream out = new DataOutputStream(s.getOutputStream());
      out.writeShort(request.length);
      out.write(request);
      out.flush();
      DataInputStream in = new DataInputStream(s.getInputStream());
      byte[] r = new byte[in.readUnsignedShort()];
      in.readFully(r);
      assertEquals(1, new Message(r).getSection(Section.ANSWER).size());
      // Complete client-initiated shutdown before closing the listener. Otherwise close() can
      // race the client's FIN and leave the server port in TIME_WAIT on Linux, even though the
      // listener was correctly released.
      s.shutdownOutput();
      assertEquals(-1, in.read());
    } finally {
      server.close();
    }
    assertPortReleased(port);
  }

  @Test
  public void redirectPreservesRawPathAndRejectsUnknownHost() throws Exception {
    try (RedirectServer server =
        new RedirectServer(rules(true), a -> a.isLoopbackAddress(), s -> {})) {
      server.start(InetAddress.getLoopbackAddress(), 0);
      String r = http(server.port(), "assets1.xboxlive.com", "/file%2Fa%20b?token=A%2BB");
      assertTrue(r.contains("307"));
      assertTrue(r.contains("Location: http://assets1.xboxlive.cn/file%2Fa%20b?token=A%2BB"));
      assertTrue(http(server.port(), "evil.example", "/x").contains("404"));
      assertTrue(http(server.port(), "assets1.xboxlive.com", "//evil.example/x").contains("404"));
    }
  }

  private String http(int port, String host, String path) throws Exception {
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
      socket.setSoTimeout(2000);
      socket
          .getOutputStream()
          .write(
              ("GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n")
                  .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      return new String(
          socket.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII);
    }
  }

  @Test
  public void benchmarkPinsIpPreservesHostAndCapsBody() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse()
              .setBody(new Buffer().write(new byte[262144]))
              .setHeader("Content-Type", "application/octet-stream"));
      server.start();
      Benchmark b = new Benchmark(SocketFactory.getDefault());
      Candidate result =
          b.measure(
              new Candidate("127.0.0.1", "test"),
              "http://cdn.invalid:" + server.getPort() + "/sample",
              3,
              131072);
      assertEquals(131072, result.bytes);
      assertTrue(result.mbps > 0);
      RecordedRequest request = server.takeRequest();
      assertEquals("cdn.invalid:" + server.getPort(), request.getHeader("Host"));
      assertEquals("bytes=0-131071", request.getHeader("Range"));
    }
  }

  @Test
  public void benchmarkRejectsHtmlRedirectsAndCancellation() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse().setResponseCode(302).setHeader("Location", "http://example.com"));
      server.enqueue(
          new MockResponse().setHeader("Content-Type", "text/html").setBody("<html>error</html>"));
      server.start();
      String url = "http://test.invalid:" + server.getPort() + "/";
      Benchmark b = new Benchmark(SocketFactory.getDefault());
      assertEquals(0, b.measure(new Candidate("127.0.0.1", ""), url, 2, 131072).mbps, 0);
      assertEquals(0, b.measure(new Candidate("127.0.0.1", ""), url, 2, 131072).mbps, 0);
      b.cancel();
      assertEquals("已取消", b.measure(new Candidate("127.0.0.1", ""), url, 2, 131072).status);
    }
  }

  @Test
  public void importDeduplicatesAndSkipsIpv6AndInvalidLines() {
    List<Candidate> c =
        Candidate.parse("XboxCn1\n1.2.3.4 test\n1.2.3.4 duplicate\n::1 test\n256.1.1.1\n5.6.7.8\n");
    assertEquals(2, c.size());
    assertEquals("test", c.get(0).label);
  }

  @Test
  public void rootRulesAreScopedAndRejectShellInput() {
    String script = RootRules.install("wlan0", "192.0.2.105", 24, true);
    assertTrue(script.contains("-d 192.0.2.105"));
    assertTrue(script.contains("-i wlan0"));
    assertTrue(script.contains("-p tcp --dport 53"));
    assertFalse(script.contains("OUTPUT"));
    assertFalse(script.contains(" -F\n"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RootRules.install("wlan0;id", "192.168.1.2", 24, false));
  }
}
