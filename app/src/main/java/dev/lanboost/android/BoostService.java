package dev.lanboost.android;

import android.app.*;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.*;
import dev.lanboost.core.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;

public final class BoostService extends Service {
  public static final String START = "start",
      STOP = "stop",
      TEST = "test",
      CANCEL = "cancel",
      FETCH = "fetch",
      ROOT = "root",
      DIAG = "diag",
      CLEAN = "clean";
  private final ExecutorService jobs = Executors.newSingleThreadExecutor();
  private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private DnsServer dns;
  private RedirectServer http;
  private volatile LanNetwork servingNetwork;
  private PowerManager.WakeLock wake;
  private WifiManager.WifiLock wifiLock;
  private boolean rootApplied;
  private static final String OWNER = "/data/local/tmp/lanboost-owner";

  @Override
  public void onCreate() {
    super.onCreate();
    AppState.init(this);
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel("service", "局域网服务", NotificationManager.IMPORTANCE_LOW));
    startForeground(7, notification());
    wake =
        ((PowerManager) getSystemService(POWER_SERVICE))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LanBoost:network");
    wake.acquire();
    wifiLock =
        ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE))
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LanBoost:WiFi");
    wifiLock.acquire();
    monitor.scheduleWithFixedDelay(
        () -> {
          if (AppState.serving || AppState.testing || AppState.busy)
            getSystemService(NotificationManager.class).notify(7, notification());
          LanNetwork net = servingNetwork;
          if (net == null) return;
          try {
            if (net.same(LanNetwork.find(this))) return;
          } catch (IOException ignored) {
          }
          servingNetwork = null;
          AppState.log("Wi-Fi 已断开或地址改变，正在停止服务");
          jobs.execute(
              () -> {
                stopDns();
                idle();
              });
        },
        2,
        2,
        TimeUnit.SECONDS);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) {
      idle();
      return START_NOT_STICKY;
    }
    String action = intent.getAction();
    if (CANCEL.equals(action)) {
      if (AppState.benchmark != null) AppState.benchmark.cancel();
      return START_NOT_STICKY;
    }
    if (STOP.equals(action)) {
      if (AppState.benchmark != null) AppState.benchmark.cancel();
      AppState.busy = true;
      AppState.serviceStatus = "停止中";
      jobs.execute(
          () -> {
            stopDns();
            AppState.busy = false;
            idle();
          });
      return START_NOT_STICKY;
    }
    if (AppState.busy || AppState.testing) return START_NOT_STICKY;
    AppState.busy = true;
    jobs.execute(
        () -> {
          try {
            if (START.equals(action)) startDns();
            else if (TEST.equals(action))
              test(
                  new NodeFilter(
                      intent.getStringExtra("operator"),
                      intent.getStringExtra("province"),
                      intent.getStringExtra("city"),
                      intent.getStringExtra("filter")),
                  intent.getBooleanExtra("nextBatch", false),
                  intent.getBooleanExtra("allCandidates", false));
            else if (FETCH.equals(action)) fetch();
            else if (ROOT.equals(action)) {
              String id = RootShell.run("id");
              if (!id.contains("uid=0")) throw new IOException("未获得 Root");
              AppState.prefs(this).edit().putString("rootStatus", "Root 已授权").apply();
              AppState.log("Root 已授权");
            } else if (CLEAN.equals(action)) {
              stopDns();
            } else if (DIAG.equals(action)) {
              diagnose();
            }
          } catch (Exception e) {
            AppState.log(e.getMessage() == null ? e.toString() : e.getMessage());
            if (START.equals(action)) {
              stopDns();
              AppState.serviceStatus = "启动失败：" + e.getMessage();
            }
            if (ROOT.equals(action))
              AppState.prefs(this).edit().putString("rootStatus", "Root 未授权").apply();
            if (TEST.equals(action)) AppState.progress = "测速失败：" + e.getMessage();
            if (FETCH.equals(action)) AppState.progress = "获取失败：" + e.getMessage();
            main.post(
                () ->
                    android.widget.Toast.makeText(
                            this, e.getMessage(), android.widget.Toast.LENGTH_LONG)
                        .show());
          } finally {
            AppState.busy = false;
            AppState.revision++;
            idle();
          }
        });
    return START_NOT_STICKY;
  }

  private void startDns() throws IOException {
    if (AppState.serving) return;
    AppState.serviceStatus = "启动中";
    LanNetwork net = LanNetwork.find(this);
    String upstream = AppState.prefs(this).getString("upstream", "223.5.5.5");
    String backup = AppState.prefs(this).getString("backup", "119.29.29.29");
    if (!Ipv4.valid(upstream) || upstream.equals(net.ip)) throw new IOException("上游 DNS 地址无效或指向本机");
    if (!backup.isEmpty() && (!Ipv4.valid(backup) || backup.equals(net.ip)))
      throw new IOException("备用 DNS 地址无效或指向本机");
    Rules rules = AppState.rules(this, net.ip);
    if (!RootShell.run("id").contains("uid=0")) throw new IOException("未获得 Root 授权");
    AppState.prefs(this).edit().putString("rootStatus", "Root 已授权").apply();
    boolean redirect = AppState.httpRedirectActive(this);
    if (AppState.httpRedirectEnabled(this) && !redirect)
      AppState.log("国内 HTTP 换源等待 CN1 节点；本次仅启动 DNS");
    dns =
        new DnsServer(
            new DnsEngine(
                rules,
                backup.isEmpty()
                    ? new DnsUpstream(upstream, 53, net.binder())
                    : new FailoverUpstream(
                        new DnsUpstream(upstream, 53, net.binder()),
                        new DnsUpstream(backup, 53, net.binder()))),
            net::allows,
            msg -> {
              AppState.recordDns(msg, rules);
            });
    dns.start(Ipv4.address(net.ip), 1053);
    if (redirect) {
      http = new RedirectServer(rules, net::allows, AppState::log);
      http.start(Ipv4.address(net.ip), 1080);
    }
    String token = Long.toString(System.currentTimeMillis());
    int pid = android.os.Process.myPid();
    // A detached root watcher removes our NAT chain if Android kills the app process.
    String watcher =
        "while [ \"$(cat "
            + OWNER
            + " 2>/dev/null)\" = \""
            + token
            + "\" ] && kill -0 "
            + pid
            + " 2>/dev/null; do sleep 2; done\n"
            + "if [ \"$(cat "
            + OWNER
            + " 2>/dev/null)\" = \""
            + token
            + "\" ]; then\n"
            + RootRules.cleanup()
            + "rm -f "
            + OWNER
            + "\nfi\n";
    File script = new File(getFilesDir(), "watchdog.sh");
    try (FileOutputStream out = new FileOutputStream(script)) {
      out.write(watcher.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    AppState.prefs(this).edit().putBoolean("cleanupNeeded", true).commit();
    rootApplied = true;
    RootShell.run(
        "printf '%s' '"
            + token
            + "' > "
            + OWNER
            + "\n"
            + RootRules.install(net.iface, net.ip, net.prefix, redirect)
            + "nohup /system/bin/sh '"
            + script.getAbsolutePath()
            + "' >/dev/null 2>&1 </dev/null &\n");
    if (!net.same(LanNetwork.find(this))) throw new IOException("启动时 Wi-Fi 地址发生变化");
    servingNetwork = net;
    AppState.queries.set(0);
    AppState.mappedQueries.set(0);
    AppState.serviceStarted = System.currentTimeMillis();
    synchronized (AppState.LOCK) {
      AppState.clients.clear();
      AppState.events.clear();
    }
    AppState.serving = true;
    AppState.serviceIp = net.ip;
    AppState.serviceStatus = redirect ? "DNS + HTTP 换源运行中" : "DNS 运行中";
    AppState.log(AppState.serviceStatus + " · " + net.ip + ":53 · " + net.iface);
  }

  private void stopDns() {
    servingNetwork = null;
    boolean cleanupOk = true;
    if (rootApplied || AppState.prefs(this).getBoolean("cleanupNeeded", false)) {
      try {
        RootShell.run("rm -f " + OWNER + "\n" + RootRules.cleanup());
        rootApplied = false;
        AppState.prefs(this).edit().putBoolean("cleanupNeeded", false).commit();
        AppState.log("Root 端口规则已清理");
      } catch (IOException e) {
        cleanupOk = false;
        AppState.log("清理失败，需要重试：" + e.getMessage());
      }
    }
    if (dns != null) {
      dns.close();
      dns = null;
    }
    if (http != null) {
      http.close();
      http = null;
    }
    AppState.serving = false;
    AppState.serviceIp = "";
    AppState.serviceStatus = cleanupOk ? "已停止" : "端口规则待清理";
  }

  private void fetch() {
    Profile p = AppState.profile;
    AppState.progress = "获取 " + p.label + " 完整节点库";
    AppState.revision++;
    // Catalogs follow the system's active network/proxy/VPN. Only probes and LAN DNS bind Wi-Fi.
    OkHttpClient client =
        new OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    try {
      NodeCatalogFetcher.Result result =
          new NodeCatalogFetcher(client)
              .fetch(
                  p,
                  stage -> {
                    AppState.progress = stage;
                    AppState.revision++;
                  });
      NodeStore.success(this, p, result, System.currentTimeMillis());
      AppState.progress = "完整节点库已更新 · " + result.nodes.size() + " 个 · " + result.source;
      AppState.log(AppState.progress);
    } catch (IOException unavailable) {
      String error = unavailable.getMessage();
      boolean cached = NodeStore.failure(this, p, error, System.currentTimeMillis());
      AppState.log("完整节点库获取失败：" + error);
      if (cached) {
        AppState.progress = "更新失败 · 继续使用完整缓存";
      } else {
        try {
          List<Candidate> found = dnsCandidates(p);
          NodeStore.add(this, p, found, "dns");
          AppState.progress = "完整库不可用 · 当前使用 DNS 临时候选";
        } catch (Exception fallback) {
          NodeStore.failure(
              this,
              p,
              error
                  + "\nDNS 候选："
                  + (fallback instanceof IOException
                      ? NodeCatalogFetcher.describe((IOException) fallback)
                      : "解析失败"),
              System.currentTimeMillis());
          AppState.progress = "获取失败 · 原有节点已保留，可重试或手动导入";
        }
      }
      AppState.log(AppState.progress);
    } finally {
      AppState.reloadNodes(this);
      client.connectionPool().evictAll();
      client.dispatcher().executorService().shutdown();
    }
  }

  private List<Candidate> dnsCandidates(Profile p) throws IOException {
    LanNetwork net = LanNetwork.find(this);
    String testUrl = AppState.prefs(this).getString("url." + p.name(), p.url);
    String host = testUrl.isEmpty() ? p.defaultHost() : HttpUrl.get(testUrl).host();
    org.xbill.DNS.Message query =
        org.xbill.DNS.Message.newQuery(
            org.xbill.DNS.Record.newRecord(
                org.xbill.DNS.Name.fromString(host + "."),
                org.xbill.DNS.Type.A,
                org.xbill.DNS.DClass.IN));
    org.xbill.DNS.Message response =
        new DnsUpstream(AppState.prefs(this).getString("upstream", "223.5.5.5"), 53, net.binder())
            .resolve(query);
    List<Candidate> found = new ArrayList<>();
    for (org.xbill.DNS.Record record : response.getSection(org.xbill.DNS.Section.ANSWER)) {
      if (record instanceof org.xbill.DNS.ARecord) {
        Candidate c =
            new Candidate(
                ((org.xbill.DNS.ARecord) record).getAddress().getHostAddress(), "当前 DNS · " + host);
        c.origin = "dns";
        found.add(c);
      }
    }
    if (found.isEmpty()) throw new IOException("没有 IPv4 候选");
    return found;
  }

  private void test(NodeFilter filter, boolean nextBatch, boolean all) throws Exception {
    LanNetwork net = LanNetwork.find(this);
    Profile profile = AppState.profile;
    String url = AppState.prefs(this).getString("url." + profile.name(), profile.url);
    HttpUrl parsed = url.isEmpty() ? null : HttpUrl.get(url);
    int seconds = AppState.prefs(this).getInt("seconds", 5),
        top = AppState.prefs(this).getInt("top", 12),
        capMiB = Math.max(1, Math.min(128, AppState.prefs(this).getInt("capMiB", 32)));
    BenchmarkPlan plan =
        new BenchmarkPlan(AppState.snapshot(), filter, url, nextBatch, all, top, capMiB);
    List<Candidate> candidates = plan.candidates;
    if (candidates.isEmpty()) throw new IOException("当前筛选下没有待测节点");
    Set<String> testedIps = new HashSet<>();
    for (Candidate candidate : candidates) testedIps.add(candidate.ip);
    long cap = capMiB * 1_048_576L;
    Benchmark benchmark = new Benchmark(net.network.getSocketFactory());
    AppState.benchmark = benchmark;
    AppState.testing = true;
    ExecutorService probes = Executors.newFixedThreadPool(6);
    try {
      for (Candidate c : candidates) {
        c.latencyMs = -1;
        c.status = "等待连通测试";
        AppState.update(c);
      }
      CompletionService<Candidate> completion = new ExecutorCompletionService<>(probes);
      for (Candidate c : candidates)
        completion.submit(
            () -> {
              c.latencyMs = benchmark.latency(c.ip, parsed == null ? 80 : parsed.port());
              c.status = c.latencyMs < 0 ? "连接失败" : parsed == null ? "连通 · 仅测延迟" : "待下载测速";
              return c;
            });
      List<Candidate> reachable = new ArrayList<>();
      for (int i = 0; i < candidates.size() && !benchmark.cancelled(); i++) {
        Candidate c = completion.take().get();
        AppState.update(c);
        if (c.latencyMs >= 0) reachable.add(c);
        AppState.progress = "连通测试 " + (i + 1) + "/" + candidates.size();
      }
      reachable.sort(Comparator.comparingLong(c -> c.latencyMs));
      int count = plan.downloadsForReachable(reachable.size());
      for (int i = 0; i < count && !benchmark.cancelled(); i++) {
        if (!net.same(LanNetwork.find(this))) throw new IOException("Wi-Fi 已改变，测速停止");
        Candidate node = reachable.get(i);
        node.status = "下载测速中";
        AppState.update(node);
        AppState.progress = "下载测速 " + (i + 1) + "/" + count + " · " + node.ip;
        Candidate result = benchmark.measure(node, url, seconds, cap);
        if (!benchmark.cancelled()) {
          result.throughputAttempted = true;
          result.testedUrl = url;
          result.measuredAt = System.currentTimeMillis();
        }
        AppState.update(result);
        AppState.log(
            result.ip
                + " · "
                + result.status
                + " · "
                + String.format(Locale.ROOT, "%.1f Mbps", result.mbps));
      }
      AppState.progress =
          benchmark.cancelled()
              ? "测速已取消"
              : parsed == null
                  ? "连通性完成 · " + reachable.size() + " 个可达，未测下载速度"
                  : "测速完成 · " + count + " 个节点";
      for (Candidate c : AppState.snapshot()) {
        if (!testedIps.contains(c.ip)) continue;
        if (c.status.equals("待下载测速") || c.status.equals("等待连通测试") || c.status.equals("下载测速中")) {
          c.status = benchmark.cancelled() ? "已取消" : "未测吞吐 · 超出本轮上限";
          AppState.update(c);
        }
      }
      if (!benchmark.cancelled())
        AppState.prefs(this)
            .edit()
            .putLong("tested." + profile.name(), System.currentTimeMillis())
            .apply();
    } finally {
      benchmark.cancel();
      probes.shutdownNow();
      AppState.testing = false;
      AppState.benchmark = null;
      AppState.save(this);
    }
  }

  private void diagnose() throws IOException {
    LanNetwork net = LanNetwork.find(this);
    AppState.log("诊断 · Wi-Fi " + net.ip + "/" + net.prefix + " · " + net.iface);
    org.xbill.DNS.Message query =
        org.xbill.DNS.Message.newQuery(
            org.xbill.DNS.Record.newRecord(
                org.xbill.DNS.Name.fromString("example.com."),
                org.xbill.DNS.Type.A,
                org.xbill.DNS.DClass.IN));
    for (String key : new String[] {"upstream", "backup"}) {
      String ip =
          AppState.prefs(this)
              .getString(key, key.equals("upstream") ? "223.5.5.5" : "119.29.29.29");
      if (ip.isEmpty()) continue;
      long start = System.nanoTime();
      try {
        org.xbill.DNS.Message answer = new DnsUpstream(ip, 53, net.binder()).resolve(query);
        AppState.log(
            "诊断 · "
                + (key.equals("upstream") ? "主" : "备用")
                + " DNS "
                + ip
                + " · "
                + org.xbill.DNS.Rcode.string(answer.getRcode())
                + " · "
                + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                + " ms");
      } catch (IOException e) {
        AppState.log("诊断 · DNS " + ip + " 失败：" + e.getMessage());
      }
    }
    if (AppState.serving) {
      org.xbill.DNS.Message answer = new DnsUpstream(net.ip, 1053, net.binder()).resolve(query);
      AppState.log(
          "诊断 · 本机 DNS 应答 " + org.xbill.DNS.Rcode.string(answer.getRcode()) + "；设备是否接入请查看请求记录");
    } else AppState.log("诊断 · DNS 服务尚未启动");
  }

  private Notification notification() {
    PendingIntent open =
        PendingIntent.getActivity(
            this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, BoostService.class).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this, "service")
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("LanBoost")
        .setContentText(AppState.testing ? AppState.progress : AppState.serviceStatus)
        .setContentIntent(open)
        .setOngoing(true)
        .addAction(
            new Notification.Action.Builder(android.R.drawable.ic_media_pause, "停止", stop).build())
        .build();
  }

  private void idle() {
    main.post(
        () -> {
          if (!AppState.serving && !AppState.testing && !AppState.busy) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
          } else getSystemService(NotificationManager.class).notify(7, notification());
        });
  }

  @Override
  public void onDestroy() {
    if (AppState.benchmark != null) AppState.benchmark.cancel();
    monitor.shutdownNow();
    jobs.execute(
        () -> {
          if (dns != null || http != null || rootApplied) stopDns();
        });
    jobs.shutdown();
    if (wake != null && wake.isHeld()) wake.release();
    if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
