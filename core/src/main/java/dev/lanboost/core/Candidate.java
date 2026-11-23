package dev.lanboost.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class Candidate {
  public final String ip;
  public String label;
  public long latencyMs = -1, bytes;
  public double mbps;
  public String status = "待测速";
  public String origin = "manual", testedUrl = "";
  public long measuredAt;
  public boolean throughputAttempted;

  public Candidate(String ip, String label) {
    if (!Ipv4.valid(ip)) throw new IllegalArgumentException("Invalid IPv4");
    this.ip = ip;
    this.label = label;
  }

  public Candidate copy() {
    Candidate c = new Candidate(ip, label);
    c.latencyMs = latencyMs;
    c.bytes = bytes;
    c.mbps = mbps;
    c.status = status;
    c.origin = origin;
    c.testedUrl = testedUrl;
    c.measuredAt = measuredAt;
    c.throughputAttempted = throughputAttempted;
    return c;
  }

  public boolean testedFor(String url) {
    return throughputAttempted && testedUrl.equals(url);
  }

  public static List<Candidate> parse(String text) {
    LinkedHashMap<String, Candidate> nodes = new LinkedHashMap<>();
    for (String line : text.split("\\R")) {
      String[] parts = line.trim().split("\\s+", 2);
      if (parts.length > 0 && Ipv4.valid(parts[0])) {
        nodes.putIfAbsent(parts[0], new Candidate(parts[0], parts.length > 1 ? parts[1] : "手动导入"));
        if (nodes.size() >= 2048) break;
      }
    }
    return new ArrayList<>(nodes.values());
  }
}
