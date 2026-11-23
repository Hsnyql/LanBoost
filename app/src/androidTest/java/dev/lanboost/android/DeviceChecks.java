package dev.lanboost.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import dev.lanboost.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.net.SocketFactory;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

/** On-device protocol checks. No root, persisted settings, or external downloads. */
public final class DeviceChecks extends Instrumentation {
  private Bundle arguments;
  private final StringBuilder report = new StringBuilder();

  @Override
  public void onCreate(Bundle args) {
    arguments = args;
    start();
  }

  private synchronized void passed(String text) {
    report.append(text).append('\n');
    Bundle status = new Bundle();
    status.putString("stream", text + "\n");
    sendStatus(0, status);
  }

  private static void check(boolean condition, String text) {
    if (!condition) throw new AssertionError(text);
  }

  private static Message query(int type) throws IOException {
    return Message.newQuery(
        Record.newRecord(Name.fromString("assets1.xboxlive.cn."), type, DClass.IN));
  }

  private static Rules fixtureRules(String local) {
    Map<Profile, String> selections = new EnumMap<>(Profile.class);
    for (Profile p : Profile.values()) selections.put(p, "192.0.2." + (11 + p.ordinal()));
    return new Rules(
        selections, EnumSet.allOf(Profile.class), local, true, true, Collections.emptyMap());
  }

  private void checkRedirectDefaults() throws Exception {
    // Isolate preference scenarios from the installed app's real settings.
    SharedPreferences fixture = getContext().getSharedPreferences("redirect-default-checks", 0);
    Context context =
        new ContextWrapper(getTargetContext()) {
          @Override
          public SharedPreferences getSharedPreferences(String name, int mode) {
            return fixture;
          }
        };
    fixture.edit().clear().commit();
    try {
      check(AppState.httpRedirectEnabled(context), "Fresh install defaults to HTTP redirection");
      check(!AppState.httpRedirectActive(context), "No CN1 keeps HTTP redirection inactive");
      fixture
          .edit()
          .putString("selected.GLOBAL", "192.0.2.13")
          .putString("selected.NS", "192.0.2.20")
          .commit();
      checkRedirectAnswer(context, "assets1.xboxlive.com", "192.0.2.13");
      checkRedirectAnswer(context, "ctest-dl-lp1.cdn.nintendo.net", "192.0.2.20");
      fixture.edit().putString("selected.CN1", "192.0.2.11").commit();
      check(AppState.httpRedirectActive(context), "Selecting CN1 activates default redirection");
      checkRedirectAnswer(context, "assets1.xboxlive.com", "192.0.2.105");
      checkRedirectAnswer(context, "assets1.xboxlive.cn", "192.0.2.11");
      fixture.edit().putBoolean("redirect", false).commit();
      check(!AppState.httpRedirectEnabled(context), "Persisted manual opt-out remains off");
      checkRedirectAnswer(context, "assets1.xboxlive.com", "192.0.2.13");
      fixture.edit().putBoolean("redirect", true).remove("selected.CN1").commit();
      check(!AppState.httpRedirectActive(context), "Removing CN1 deactivates redirection");
      checkRedirectAnswer(context, "assets1.xboxlive.com", "192.0.2.13");
      passed(
          "PASS HTTP redirect defaults, no-CN1/other-platform DNS, activation and saved opt-out");
    } finally {
      fixture.edit().clear().commit();
    }
  }

  private void checkRedirectAnswer(Context context, String host, String expected) throws Exception {
    DnsEngine engine =
        new DnsEngine(
            AppState.rules(context, "192.0.2.105"),
            q -> {
              throw new IOException("Unexpected upstream query");
            });
    Message q = Message.newQuery(Record.newRecord(Name.fromString(host + "."), Type.A, DClass.IN));
    Message answer = new Message(engine.answer(q.toWire(), false));
    check(
        answer.getRcode() == Rcode.NOERROR && answer.getSection(Section.ANSWER).size() == 1,
        "DNS answer for " + host);
    check(
        ((ARecord) answer.getSection(Section.ANSWER).get(0))
            .getAddress()
            .getHostAddress()
            .equals(expected),
        "DNS target for " + host);
  }

  private void checkNodeStore() throws Exception {
    SharedPreferences fixture = getContext().getSharedPreferences("catalog-state-checks", 0);
    Context context =
        new ContextWrapper(getTargetContext()) {
          @Override
          public SharedPreferences getSharedPreferences(String name, int mode) {
            return fixture;
          }
        };
    fixture.edit().clear().commit();
    try {
      fixture
          .edit()
          .putString(
              "nodes.CN1",
              "[{\"ip\":\"192.0.2.9\",\"label\":\"当前 DNS ·"
                  + " example.com\",\"mbps\":123,\"bytes\":65536}]")
          .putString("selected.CN1", "192.0.2.9")
          .putLong("tested.CN1", 100)
          .commit();
      check(
          NodeStore.read(context, Profile.CN1).get(0).origin.equals("dns"),
          "Legacy DNS provenance");
      check(
          NodeStore.read(context, Profile.CN1).get(0).testedFor(Profile.CN1.url),
          "Legacy measurements migrated");
      check(
          !NodeStore.failure(context, Profile.CN1, "network failure", 150),
          "No fabricated full cache");
      NodeStore.add(
          context, Profile.CN1, Arrays.asList(new Candidate("192.0.2.10", "DNS example")), "dns");
      check(
          NodeStore.read(context, Profile.CN1).size() == 2,
          "DNS candidates merge without data loss");
      NodeStore.success(
          context,
          Profile.CN1,
          new NodeCatalogFetcher.Result(
              Arrays.asList(
                  new Candidate("192.0.2.9", "region"), new Candidate("192.0.2.11", "region 2")),
              "GitHub API"),
          200);
      check(
          NodeStore.catalog(context, Profile.CN1).size() == 2, "Complete catalog saved separately");
      check(NodeStore.read(context, Profile.CN1).size() == 3, "Additional candidates preserved");
      Candidate restored = NodeStore.read(context, Profile.CN1).get(0);
      check(
          restored.mbps == 123 && restored.measuredAt == 100 && restored.testedFor(Profile.CN1.url),
          "Measurements survive catalog refresh");
      check(AppState.selected(context, Profile.CN1).equals("192.0.2.9"), "Selected node survives");
      check(NodeStore.error(context, Profile.CN1).isEmpty(), "Success clears prior error");
      check(
          NodeStore.failure(context, Profile.CN1, "HTTP 503", 300),
          "Failure retains complete cache");
      check(
          fixture.getLong("catalogTime.CN1", 0) == 200,
          "Failure does not change successful update time");
      check(
          NodeStore.catalog(context, Profile.CN1).size() == 2
              && NodeStore.read(context, Profile.CN1).get(0).mbps == 123,
          "Cache and results survive failure");
      check(
          NodeStore.read(context, Profile.NS).isEmpty()
              && NodeStore.error(context, Profile.NS).isEmpty(),
          "Profile isolation");
      passed(
          "PASS catalog persistence, legacy migration, fallback/cache state, profile isolation and"
              + " selected-node/result preservation");
    } finally {
      fixture.edit().clear().commit();
    }
  }

  /** Explicit emulator-only integration check; restores all app preferences afterward. */
  private void checkFilteredService() throws Exception {
    check(
        android.os.Build.FINGERPRINT.contains("generic")
            || android.os.Build.MODEL.contains("sdk_gphone"),
        "Synthetic service fixture is emulator-only");
    Context context = getTargetContext();
    AppState.init(context);
    check(!AppState.serving && !AppState.busy && !AppState.testing, "Fixture requires idle app");
    SharedPreferences prefs = AppState.prefs(context);
    Map<String, ?> saved = new HashMap<>(prefs.getAll());
    Profile previous = AppState.profile;
    ExecutorService server = Executors.newSingleThreadExecutor();
    Activity activity = null;
    java.util.concurrent.atomic.AtomicInteger requests =
        new java.util.concurrent.atomic.AtomicInteger();
    try (ServerSocket fixture = new ServerSocket(0, 50, Ipv4.address("0.0.0.0"))) {
      fixture.setSoTimeout(500);
      Future<?> handler =
          server.submit(
              () -> {
                while (!Thread.currentThread().isInterrupted() && !fixture.isClosed()) {
                  try (Socket socket = fixture.accept()) {
                    socket.setSoTimeout(3000);
                    BufferedReader in =
                        new BufferedReader(
                            new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.US_ASCII));
                    String first = in.readLine();
                    if (first == null) continue; // TCP latency probe
                    String line, host = "";
                    while ((line = in.readLine()) != null && !line.isEmpty())
                      if (line.startsWith("Host:")) host = line;
                    check(
                        host.equals("Host: fixture.invalid:" + fixture.getLocalPort()),
                        "Service preserves Host");
                    requests.incrementAndGet();
                    OutputStream out = socket.getOutputStream();
                    out.write(
                        "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: 65536\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
                    out.write(new byte[65536]);
                    out.flush();
                  } catch (SocketTimeoutException ignored) {
                  } catch (IOException e) {
                    if (!fixture.isClosed()) throw new UncheckedIOException(e);
                  }
                }
              });
      String url = "http://fixture.invalid:" + fixture.getLocalPort() + "/sample";
      List<Candidate> nodes = new ArrayList<>();
      for (int i = 1; i <= 15; i++) nodes.add(new Candidate("127.0.0." + i, "中国广东深圳 电信"));
      Candidate old = new Candidate("127.0.0.16", "中国广东深圳 电信");
      old.throughputAttempted = true;
      old.testedUrl = url;
      old.mbps = 777;
      old.measuredAt = 100;
      old.status = "完成";
      nodes.add(old);
      nodes.add(new Candidate("127.0.0.17", "中国广东深圳 移动"));
      nodes.add(new Candidate("127.0.0.18", "中国广东广州 电信"));
      nodes.add(new Candidate("127.0.0.19", "中国浙江杭州 电信"));
      nodes.add(new Candidate("192.0.2.20", "中国广东深圳 电信"));
      prefs
          .edit()
          .putString("url.CN1", url)
          .putInt("top", 12)
          .putInt("capMiB", 1)
          .putInt("seconds", 2)
          .commit();
      NodeStore.save(context, Profile.CN1, nodes);
      AppState.switchProfile(context, Profile.CN1);
      activity =
          startActivitySync(
              new android.content.Intent(context, MainActivity.class)
                  .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
      runOnMainSync(
          () ->
              context.startForegroundService(
                  new android.content.Intent(context, BoostService.class)
                      .setAction(BoostService.TEST)
                      .putExtra("operator", "电信")
                      .putExtra("province", "广东")
                      .putExtra("city", "深圳")
                      .putExtra("filter", "127.0.0")
                      .putExtra("nextBatch", true)
                      .putExtra("allCandidates", true)));
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
      while (System.nanoTime() < deadline) {
        if (!AppState.busy && !AppState.testing && AppState.progress.startsWith("测速完成")) break;
        Thread.sleep(100);
      }
      check(!AppState.busy && !AppState.testing, "Filtered service completed within deadline");
      check(
          AppState.progress.equals("测速完成 · 15 个节点"), "All mode completes 15: " + AppState.progress);
      List<Candidate> results = AppState.snapshot();
      for (int i = 0; i < 15; i++)
        check(
            results.get(i).testedFor(url)
                && results.get(i).bytes == 65536
                && results.get(i).mbps > 0,
            "Measured filtered node " + i);
      check(
          results.get(15).mbps == 777 && results.get(15).measuredAt == 100,
          "Continuation preserves prior measurement");
      for (int i = 16; i < results.size(); i++)
        check(
            !results.get(i).throughputAttempted && results.get(i).latencyMs == -1,
            "Excluded node untouched");
      check(requests.get() == 15, "Only fifteen filtered pending nodes downloaded");
      check(!handler.isDone(), "Fixture server remained healthy");
      passed(
          "PASS service: carrier/province/city/keyword AND, all 15 downloads beyond batch 12,"
              + " continuation and excluded nodes preserved");
    } finally {
      if (AppState.benchmark != null) AppState.benchmark.cancel();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
      while ((AppState.testing || AppState.busy) && System.nanoTime() < deadline) Thread.sleep(50);
      context.stopService(new android.content.Intent(context, BoostService.class));
      server.shutdownNow();
      if (activity != null) {
        Activity opened = activity;
        runOnMainSync(opened::finish);
      }
      SharedPreferences.Editor editor = prefs.edit().clear();
      for (Map.Entry<String, ?> entry : saved.entrySet()) {
        Object value = entry.getValue();
        String key = entry.getKey();
        if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
      }
      editor.commit();
      AppState.switchProfile(context, previous);
      if (!saved.containsKey("profile")) prefs.edit().remove("profile").commit();
    }
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    int outcome = Activity.RESULT_OK;
    try {
      checkRedirectDefaults();
      checkNodeStore();
      if ("true".equals(arguments.getString("filtered_service"))) checkFilteredService();
      Rules rules = fixtureRules("127.0.0.1");
      DnsEngine engine =
          new DnsEngine(
              rules,
              q -> {
                throw new IOException("offline test upstream");
              });
      try (DnsServer dns = new DnsServer(engine, InetAddress::isLoopbackAddress, s -> {})) {
        dns.start(Ipv4.address("127.0.0.1"), 0);
        Message answer =
            new DnsUpstream("127.0.0.1", dns.port(), DnsUpstream.DEFAULT).resolve(query(Type.A));
        check(
            ((ARecord) answer.getSection(Section.ANSWER).get(0))
                .getAddress()
                .getHostAddress()
                .equals("192.0.2.11"),
            "UDP mapping");
        check(
            new DnsUpstream("127.0.0.1", dns.port(), DnsUpstream.DEFAULT)
                .resolve(query(Type.AAAA))
                .getSection(Section.ANSWER)
                .isEmpty(),
            "AAAA isolation");
        for (String host :
            new String[] {
              "dl.delivery.mp.microsoft.com",
              "tlu.dl.delivery.mp.microsoft.com",
              "1d.tlu.dl.delivery.mp.microsoft.com"
            }) {
          Message appQuery =
              Message.newQuery(Record.newRecord(Name.fromString(host + "."), Type.A, DClass.IN));
          Message appAnswer =
              new DnsUpstream("127.0.0.1", dns.port(), DnsUpstream.DEFAULT).resolve(appQuery);
          check(
              ((ARecord) appAnswer.getSection(Section.ANSWER).get(0))
                  .getAddress()
                  .getHostAddress()
                  .equals("192.0.2.14"),
              "Application mapping " + host);
        }
        for (Profile p :
            new Profile[] {Profile.NS, Profile.PS, Profile.EA, Profile.BATTLE, Profile.AKAMAI}) {
          String host = p.defaultHost();
          Message q =
              Message.newQuery(Record.newRecord(Name.fromString(host + "."), Type.A, DClass.IN));
          Message a = new DnsUpstream("127.0.0.1", dns.port(), DnsUpstream.DEFAULT).resolve(q);
          check(
              ((ARecord) a.getSection(Section.ANSWER).get(0))
                  .getAddress()
                  .getHostAddress()
                  .equals("192.0.2." + (11 + p.ordinal())),
              "Platform mapping " + p);
        }
        Message blockedQuery =
            Message.newQuery(
                Record.newRecord(
                    Name.fromString("atum-eda.hac.lp1.d4c.nintendo.net."), Type.A, DClass.IN));
        Message blockedReply =
            new DnsUpstream("127.0.0.1", dns.port(), DnsUpstream.DEFAULT).resolve(blockedQuery);
        check(
            ((ARecord) blockedReply.getSection(Section.ANSWER).get(0))
                .getAddress()
                .getHostAddress()
                .equals("0.0.0.0"),
            "Nintendo fallback block");
        try (Socket socket = new Socket("127.0.0.1", dns.port())) {
          socket.setSoTimeout(3000);
          byte[] q = query(Type.A).toWire();
          DataOutputStream out = new DataOutputStream(socket.getOutputStream());
          out.writeShort(q.length);
          out.write(q);
          out.flush();
          DataInputStream in = new DataInputStream(socket.getInputStream());
          byte[] wire = new byte[in.readUnsignedShort()];
          in.readFully(wire);
          check(new Message(wire).getSection(Section.ANSWER).size() == 1, "TCP mapping");
        }
      }
      passed(
          "PASS Android UDP/TCP DNS, Xbox/APP/NS/PS/EA/Battle/Akamai mappings, Nintendo fallback"
              + " block and AAAA suppression");
      try (RedirectServer redirect =
          new RedirectServer(rules, InetAddress::isLoopbackAddress, s -> {})) {
        redirect.start(Ipv4.address("127.0.0.1"), 0);
        try (Socket socket = new Socket("127.0.0.1", redirect.port())) {
          socket.setSoTimeout(3000);
          socket
              .getOutputStream()
              .write(
                  "HEAD /a%2Fb?x=1 HTTP/1.1\r\nHost: assets1.xboxlive.com\r\nConnection: close\r\n\r\n"
                      .getBytes(StandardCharsets.US_ASCII));
          BufferedReader in =
              new BufferedReader(
                  new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
          check(in.readLine().contains("307"), "HTTP status");
          String line;
          boolean location = false;
          while ((line = in.readLine()) != null && !line.isEmpty())
            if (line.equals("Location: http://assets1.xboxlive.cn/a%2Fb?x=1")) location = true;
          check(location, "HTTP raw path and location");
        }
      }
      passed("PASS Android HTTP redirect and encoded path");
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try (ServerSocket fixture = new ServerSocket(0, 1, Ipv4.address("127.0.0.1"))) {
        fixture.setSoTimeout(5000);
        Future<String> received =
            executor.submit(
                () -> {
                  try (Socket socket = fixture.accept()) {
                    socket.setSoTimeout(3000);
                    BufferedReader input =
                        new BufferedReader(
                            new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.US_ASCII));
                    String line, host = "";
                    while ((line = input.readLine()) != null && !line.isEmpty())
                      if (line.startsWith("Host:")) host = line;
                    OutputStream out = socket.getOutputStream();
                    out.write(
                        "HTTP/1.1 206 Partial Content\r\nContent-Type: application/octet-stream\r\nContent-Length: 262144\r\nContent-Range: bytes 0-262143/262144\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
                    out.write(new byte[262144]);
                    out.flush();
                    return host;
                  }
                });
        Benchmark benchmark = new Benchmark(SocketFactory.getDefault());
        Candidate measured =
            benchmark.measure(
                new Candidate("127.0.0.1", "local fixture"),
                "http://fixture.invalid:" + fixture.getLocalPort() + "/sample",
                3,
                262144);
        check(
            measured.bytes == 262144 && measured.mbps > 0, "Bounded download: " + measured.status);
        check(
            received
                .get(5, TimeUnit.SECONDS)
                .equals("Host: fixture.invalid:" + fixture.getLocalPort()),
            "Host preservation");
        benchmark.cancel();
        check(
            benchmark
                .measure(measured, "http://fixture.invalid/sample", 2, 262144)
                .status
                .equals("已取消"),
            "Cancellation");
      } finally {
        executor.shutdownNow();
      }
      passed("PASS Android pinned download, byte cap, Host and cancellation");
      LanNetwork net = LanNetwork.find(getTargetContext());
      passed("Wi-Fi detected: " + net.ip + "/" + net.prefix + " on " + net.iface);
      if ("true".equals(arguments.getString("platform_probe"))) {
        for (Profile p : new Profile[] {Profile.NS, Profile.PS, Profile.EA}) {
          try {
            Message q =
                Message.newQuery(
                    Record.newRecord(Name.fromString(p.defaultHost() + "."), Type.A, DClass.IN));
            Message a = new DnsUpstream("223.5.5.5", 53, net.binder()).resolve(q);
            String candidate = null;
            for (Record r : a.getSection(Section.ANSWER))
              if (r instanceof ARecord) {
                candidate = ((ARecord) r).getAddress().getHostAddress();
                break;
              }
            if (candidate == null) throw new IOException("No IPv4 candidate");
            Benchmark probe = new Benchmark(net.network.getSocketFactory());
            Candidate measured =
                probe.measure(new Candidate(candidate, "live probe"), p.url, 3, 262144);
            passed(
                "LIVE "
                    + p.name()
                    + " "
                    + candidate
                    + " "
                    + measured.status
                    + " bytes="
                    + measured.bytes
                    + " Mbps="
                    + measured.mbps);
          } catch (IOException e) {
            passed("LIVE " + p.name() + " unavailable: " + e.getMessage());
          }
        }
      }
      if ("true".equals(arguments.getString("ns_alternatives"))) {
        for (String url :
            new String[] {
              "https://ctest-dl-lp1.cdn.nintendo.net/30m",
              "http://ctest-dl.p01.lp1.ctest.srv.nintendo.net/30m"
            }) {
          try {
            String host = okhttp3.HttpUrl.get(url).host();
            Message q =
                Message.newQuery(Record.newRecord(Name.fromString(host + "."), Type.A, DClass.IN));
            Message a = new DnsUpstream("223.5.5.5", 53, net.binder()).resolve(q);
            String ip = null;
            for (Record r : a.getSection(Section.ANSWER))
              if (r instanceof ARecord) {
                ip = ((ARecord) r).getAddress().getHostAddress();
                break;
              }
            if (ip == null) throw new IOException("No IPv4 candidate");
            Candidate sample =
                new Benchmark(net.network.getSocketFactory())
                    .measure(new Candidate(ip, "NS endpoint check"), url, 3, 262144);
            passed("NS URL " + url + " " + sample.status + " bytes=" + sample.bytes);
          } catch (IOException e) {
            passed("NS URL " + url + " unavailable: " + e.getMessage());
          }
        }
      }
      int hold =
          Math.min(30000, Math.max(0, Integer.parseInt(arguments.getString("lan_hold_ms", "0"))));
      if (hold > 0) {
        Rules lanRules = fixtureRules(net.ip);
        DnsEngine lanEngine =
            new DnsEngine(lanRules, new DnsUpstream("223.5.5.5", 53, net.binder()));
        try (DnsServer lan = new DnsServer(lanEngine, net::allows, s -> passed("LAN " + s));
            RedirectServer http =
                new RedirectServer(lanRules, net::allows, s -> passed("LAN " + s))) {
          lan.start(Ipv4.address(net.ip), 1053);
          http.start(Ipv4.address(net.ip), 1080);
          passed("LAN test listener ready: " + net.ip + ":1053 for " + hold + "ms");
          android.os.PowerManager.WakeLock fixtureWake =
              getTargetContext()
                  .getSystemService(android.os.PowerManager.class)
                  .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "LanBoost:device-checks");
          fixtureWake.acquire(hold + 5000L);
          try {
            Thread.sleep(hold);
          } finally {
            if (fixtureWake.isHeld()) fixtureWake.release();
          }
        }
        passed("PASS LAN listener closed");
      }
    } catch (Throwable failure) {
      outcome = Activity.RESULT_CANCELED;
      StringWriter trace = new StringWriter();
      failure.printStackTrace(new PrintWriter(trace));
      report.append("FAIL ").append(trace);
    }
    result.putString("stream", report.toString());
    finish(outcome, result);
  }
}
