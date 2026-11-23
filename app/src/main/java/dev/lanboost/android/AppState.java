package dev.lanboost.android;

import android.content.*;
import dev.lanboost.core.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class AppState {
  public static final Object LOCK = new Object();
  public static final List<Candidate> nodes = new ArrayList<>();
  public static final ArrayDeque<String> logs = new ArrayDeque<>();
  public static final AtomicLong queries = new AtomicLong();
  public static final AtomicLong mappedQueries = new AtomicLong();
  public static volatile long serviceStarted;
  public static final Set<String> clients = new HashSet<>();
  public static final ArrayDeque<QueryEvent> events = new ArrayDeque<>();

  public static final class QueryEvent {
    public final long at = System.currentTimeMillis();
    public final String client, type, domain, result, target;

    QueryEvent(String client, String type, String domain, String result, String target) {
      this.client = client;
      this.type = type;
      this.domain = domain;
      this.result = result;
      this.target = target;
    }
  }

  public static volatile boolean testing, serving, busy;
  public static volatile String progress = "尚无测速结果", serviceStatus = "未启动", serviceIp = "";
  public static volatile long revision, nodeRevision;
  public static volatile Benchmark benchmark;
  public static Profile profile = Profile.CN1;
  private static boolean initialized;

  public static android.content.SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("lanboost", Context.MODE_PRIVATE);
  }

  public static void init(Context c) {
    synchronized (LOCK) {
      if (initialized) return;
      initialized = true;
      try {
        profile = Profile.valueOf(prefs(c).getString("profile", "CN1"));
        nodes.addAll(NodeStore.read(c, profile));
      } catch (Exception ignored) {
      }
    }
  }

  public static void switchProfile(Context c, Profile p) {
    synchronized (LOCK) {
      profile = p;
      progress = "已切换到 " + p.label;
      nodes.clear();
      nodes.addAll(NodeStore.read(c, p));
      nodeRevision++;
      prefs(c).edit().putString("profile", p.name()).apply();
      revision++;
    }
  }

  public static void reloadNodes(Context c) {
    synchronized (LOCK) {
      nodes.clear();
      nodes.addAll(NodeStore.read(c, profile));
      revision++;
      nodeRevision++;
    }
  }

  public static void save(Context c) {
    synchronized (LOCK) {
      NodeStore.save(c, profile, nodes);
      revision++;
      nodeRevision++;
    }
  }

  public static List<Candidate> snapshot() {
    synchronized (LOCK) {
      List<Candidate> list = new ArrayList<>();
      for (Candidate c : nodes) list.add(c.copy());
      return list;
    }
  }

  public static void update(Candidate c) {
    synchronized (LOCK) {
      for (int i = 0; i < nodes.size(); i++)
        if (nodes.get(i).ip.equals(c.ip)) {
          nodes.set(i, c);
          nodeRevision++;
          break;
        }
      revision++;
    }
  }

  public static void log(String value) {
    android.util.Log.i("LanBoost", value);
    String line = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + "  " + value;
    synchronized (LOCK) {
      logs.addLast(line);
      while (logs.size() > 250) logs.removeFirst();
      revision++;
    }
  }

  public static void recordDns(String message, Rules rules) {
    String[] parts = message.split(" ", 4);
    if (parts.length == 4 && Ipv4.valid(parts[0])) {
      queries.incrementAndGet();
      String target = rules.ipFor(parts[2]);
      boolean mapped =
          target != null
              && parts[3].equals("NOERROR")
              && (parts[1].equals("A")
                  || (rules.suppressIpv6(parts[2])
                      && (parts[1].equals("AAAA")
                          || parts[1].equals("HTTPS")
                          || parts[1].equals("SVCB"))));
      if (mapped) mappedQueries.incrementAndGet();
      synchronized (LOCK) {
        if (clients.size() < 128) clients.add(parts[0]);
        events.addFirst(
            new QueryEvent(
                parts[0], parts[1], Rules.normalize(parts[2]), parts[3], mapped ? target : ""));
        while (events.size() > 100) events.removeLast();
      }
    }
    log(message);
  }

  public static List<QueryEvent> eventSnapshot() {
    synchronized (LOCK) {
      return new ArrayList<>(events);
    }
  }

  public static int clientCount() {
    synchronized (LOCK) {
      return clients.size();
    }
  }

  public static String logText() {
    synchronized (LOCK) {
      return String.join("\n", logs);
    }
  }

  public static String selected(Context c, Profile p) {
    return prefs(c).getString("selected." + p.name(), "");
  }

  public static boolean httpRedirectEnabled(Context c) {
    return prefs(c).getBoolean("redirect", true);
  }

  public static boolean httpRedirectActive(Context c) {
    return httpRedirectEnabled(c) && !selected(c, Profile.CN1).isEmpty();
  }

  public static Rules rules(Context c, String ip) {
    Map<Profile, String> selections = new EnumMap<>(Profile.class);
    Set<Profile> blocked = EnumSet.noneOf(Profile.class);
    for (Profile profile : Profile.values()) {
      selections.put(profile, selected(c, profile));
      if (prefs(c).getBoolean("blockFallbacks." + profile.name(), true)) blocked.add(profile);
    }
    return new Rules(
        selections,
        blocked,
        ip,
        httpRedirectActive(c),
        prefs(c).getBoolean("ipv4", true),
        Rules.parseCustom(prefs(c).getString("custom", "")));
  }
}
