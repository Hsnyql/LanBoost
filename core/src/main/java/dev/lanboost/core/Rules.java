package dev.lanboost.core;

import java.util.*;

public final class Rules {
  private static final Set<String> CN1 =
      new HashSet<>(
          Arrays.asList(
              "assets1.xboxlive.cn", "assets2.xboxlive.cn", "d1.xboxlive.cn", "d2.xboxlive.cn"));
  private static final Set<String> CN2 =
      new HashSet<>(Arrays.asList("dlassets.xboxlive.cn", "dlassets2.xboxlive.cn"));
  private static final Set<String> LEGACY =
      new HashSet<>(Arrays.asList("dlassets.xboxlive.com", "dlassets2.xboxlive.com"));
  private static final Set<String> GLOBAL =
      new HashSet<>(
          Arrays.asList(
              "assets1.xboxlive.com",
              "assets2.xboxlive.com",
              "d1.xboxlive.com",
              "d2.xboxlive.com",
              "xvcf1.xboxlive.com",
              "xvcf2.xboxlive.com",
              "5.xb.delivery.microsoft.com",
              "assets1.xboxlive.com.edgesuite.net",
              "cl-glcb907925.gcdn.co",
              "fg.microsoft.map.fastly.net"));
  private static final Set<String> APP =
      new HashSet<>(
          Arrays.asList(
              "dl.delivery.mp.microsoft.com",
              "tlu.dl.delivery.mp.microsoft.com",
              "1d.tlu.dl.delivery.mp.microsoft.com"));
  private final String cn1, cn2, global, app, local;
  private final boolean redirect, ipv4Only;
  private final Map<String, String> custom;
  private final Map<Profile, String> selections;
  private final Set<Profile> blockFallbacks;
  private static final Map<Profile, List<String>> EXTRA = new EnumMap<>(Profile.class);
  private static final Map<Profile, List<String>> BLOCKED = new EnumMap<>(Profile.class);

  static {
    EXTRA.put(
        Profile.NS,
        Arrays.asList(
            "atum.hac.lp1.d4c.nintendo.net", "nemof.p01.lp1.nemo.srv.nintendo.net",
            "nemof.hac.lp1.nemo.srv.nintendo.net", "ctest-ul.p01.lp1.ctest.srv.nintendo.net",
            "ctest-dl.p01.lp1.ctest.srv.nintendo.net", "ctest-ul-lp1.cdn.nintendo.net",
            "ctest-dl-lp1.cdn.nintendo.net", "*.lp1.eshop.nintendo.net",
            "*.lp1.d4c.srv.nintendo.net", "*.hac.lp1.d4c.nintendo.net"));
    EXTRA.put(
        Profile.PS,
        Arrays.asList(
            "gst.prod.dl.playstation.net",
            "gs2.ww.prod.dl.playstation.net",
            "zeus.dl.playstation.net",
            "ares.dl.playstation.net"));
    EXTRA.put(Profile.EA, Collections.singletonList("origin-a.akamaihd.net"));
    EXTRA.put(Profile.BATTLE, Arrays.asList("blzddist1-a.akamaihd.net", "downloader.battle.net"));
    EXTRA.put(
        Profile.AKAMAI,
        Arrays.asList(
            "download.xbox.com",
            "catalog.gamepass.com",
            "s7mbrstream.scene7.com",
            "*.ak.tiles.virtualearth.net",
            "*.dyn.riotcdn.net",
            "epicgames-download1.akamaized.net",
            "cdn1.epicgames.com",
            "cdn2.epicgames.com",
            "*.cdn.ubi.com",
            "*.cdn.ubisoft.com",
            "*.akamai.steamstatic.com",
            "cdn.steamstatic.com",
            "video.steamstatic.com",
            "*.akamai.net",
            "*.akamaihd.net",
            "*.akamaized.net"));
    BLOCKED.put(
        Profile.NS,
        Arrays.asList("atum-eda.hac.lp1.d4c.nintendo.net", "atum-4ff.hac.lp1.d4c.nintendo.net"));
    BLOCKED.put(Profile.EA, Arrays.asList("prod.cloudflare.cdn.ea.com", "ssl-lvlt.cdn.ea.com"));
    BLOCKED.put(Profile.BATTLE, Collections.singletonList("level3.ssl.blizzard.com"));
  }

  public Rules(
      String cn1,
      String cn2,
      String global,
      String local,
      boolean redirect,
      boolean ipv4Only,
      Map<String, String> custom) {
    this(cn1, cn2, global, "", local, redirect, ipv4Only, custom);
  }

  public Rules(
      String cn1,
      String cn2,
      String global,
      String app,
      String local,
      boolean redirect,
      boolean ipv4Only,
      Map<String, String> custom) {
    this(
        legacySelections(cn1, cn2, global, app),
        Collections.emptySet(),
        local,
        redirect,
        ipv4Only,
        custom);
  }

  public Rules(
      Map<Profile, String> selections,
      Set<Profile> blockFallbacks,
      String local,
      boolean redirect,
      boolean ipv4Only,
      Map<String, String> custom) {
    this.selections = new EnumMap<>(Profile.class);
    this.selections.putAll(selections);
    this.blockFallbacks = new HashSet<>(blockFallbacks);
    for (String ip : selections.values())
      if (!ip.isEmpty() && !Ipv4.valid(ip))
        throw new IllegalArgumentException("Invalid IPv4: " + ip);
    if (!local.isEmpty() && !Ipv4.valid(local))
      throw new IllegalArgumentException("Invalid local IPv4");
    String cn1 = selections.getOrDefault(Profile.CN1, "");
    if (redirect && (cn1.isEmpty() || local.isEmpty()))
      throw new IllegalArgumentException("国内换源需要 CN1 节点与手机地址");
    this.cn1 = cn1;
    this.cn2 = selections.getOrDefault(Profile.CN2, "");
    this.global = selections.getOrDefault(Profile.GLOBAL, "");
    this.app = selections.getOrDefault(Profile.APP, "");
    this.local = local;
    this.redirect = redirect;
    this.ipv4Only = ipv4Only;
    this.custom = new HashMap<>(custom);
  }

  private static Map<Profile, String> legacySelections(
      String cn1, String cn2, String global, String app) {
    Map<Profile, String> map = new EnumMap<>(Profile.class);
    map.put(Profile.CN1, cn1);
    map.put(Profile.CN2, cn2);
    map.put(Profile.GLOBAL, global);
    map.put(Profile.APP, app);
    return map;
  }

  private static boolean matches(String host, String pattern) {
    return pattern.startsWith("*.") ? host.endsWith(pattern.substring(1)) : host.equals(pattern);
  }

  public static List<String> blockedDomains(Profile profile) {
    return Collections.unmodifiableList(BLOCKED.getOrDefault(profile, Collections.emptyList()));
  }

  public static List<String> domains(Profile profile) {
    switch (profile) {
      case CN1:
        return new ArrayList<>(CN1);
      case CN2:
        return new ArrayList<>(CN2);
      case GLOBAL:
        List<String> global = new ArrayList<>(GLOBAL);
        global.addAll(LEGACY);
        return global;
      case APP:
        List<String> app = new ArrayList<>(APP);
        app.add("*.tlu.dl.delivery.mp.microsoft.com");
        return app;
      default:
        return new ArrayList<>(EXTRA.getOrDefault(profile, Collections.emptyList()));
    }
  }

  public static String normalize(String name) {
    String s = name.toLowerCase(Locale.ROOT);
    return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
  }

  public String ipFor(String host) {
    host = normalize(host);
    if (custom.containsKey(host)) return custom.get(host);
    if (CN1.contains(host)) return emptyToNull(cn1);
    if (CN2.contains(host)) return emptyToNull(cn2);
    if (APP.contains(host) || matches(host, "*.tlu.dl.delivery.mp.microsoft.com"))
      return emptyToNull(app);
    if (GLOBAL.contains(host)) return redirect ? local : emptyToNull(global);
    if (LEGACY.contains(host)) return redirect && !cn2.isEmpty() ? local : emptyToNull(global);
    for (Map.Entry<Profile, List<String>> entry : BLOCKED.entrySet()) {
      if (entry.getValue().contains(host)) {
        String ip = emptyToNull(selections.getOrDefault(entry.getKey(), ""));
        return ip != null && blockFallbacks.contains(entry.getKey()) ? "0.0.0.0" : null;
      }
    }
    Profile profile = profileFor(host);
    if (profile != null) return emptyToNull(selections.getOrDefault(profile, ""));
    return null;
  }

  public static Profile profileFor(String host) {
    host = normalize(host);
    if (CN1.contains(host)) return Profile.CN1;
    if (CN2.contains(host)) return Profile.CN2;
    if (APP.contains(host) || matches(host, "*.tlu.dl.delivery.mp.microsoft.com"))
      return Profile.APP;
    if (GLOBAL.contains(host) || LEGACY.contains(host)) return Profile.GLOBAL;
    for (Map.Entry<Profile, List<String>> entry : BLOCKED.entrySet())
      if (entry.getValue().contains(host)) return entry.getKey();
    for (Map.Entry<Profile, List<String>> entry : EXTRA.entrySet())
      for (String pattern : entry.getValue()) if (matches(host, pattern)) return entry.getKey();
    return null;
  }

  public boolean suppressIpv6(String name) {
    String ip = ipFor(name);
    return "0.0.0.0".equals(ip) || ipv4Only && ip != null;
  }

  public String redirectHost(String host) {
    host = normalize(host);
    if (!redirect || custom.containsKey(host)) return null;
    if (GLOBAL.contains(host)) return "assets1.xboxlive.cn";
    if (LEGACY.contains(host) && !cn2.isEmpty()) return "dlassets.xboxlive.cn";
    return null;
  }

  public static Map<String, String> parseCustom(String input) {
    Map<String, String> map = new LinkedHashMap<>();
    for (String raw : input.split("\\R")) {
      String line = raw.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      String[] parts = line.split("\\s+|=", 2);
      if (parts.length != 2
          || !parts[0].matches("(?i)[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?\\.?")
          || !Ipv4.valid(parts[1].trim()))
        throw new IllegalArgumentException("规则格式：域名 IP，例如 example.com 192.168.1.10");
      map.put(normalize(parts[0]), parts[1].trim());
    }
    return map;
  }

  private static String emptyToNull(String s) {
    return s.isEmpty() ? null : s;
  }
}
