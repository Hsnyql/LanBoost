package dev.lanboost.core;

import java.util.*;

public final class NodeLists {
  private NodeLists() {}

  /** Catalog metadata replaces labels, while existing measurements and selected IP survive. */
  public static List<Candidate> mergeCatalog(
      List<Candidate> existing, List<Candidate> catalog, String selected) {
    Map<String, Candidate> previous = new LinkedHashMap<>(), merged = new LinkedHashMap<>();
    for (Candidate c : existing) previous.put(c.ip, c);
    for (Candidate entry : catalog) {
      Candidate c = previous.containsKey(entry.ip) ? previous.get(entry.ip).copy() : entry.copy();
      c.label = entry.label;
      c.origin = "catalog";
      merged.put(c.ip, c);
    }
    for (Candidate entry : existing) {
      if (merged.containsKey(entry.ip)) continue;
      if (!entry.origin.equals("catalog")
          || entry.measuredAt > 0
          || entry.mbps > 0
          || entry.latencyMs >= 0
          || !entry.status.equals("待测速")
          || entry.ip.equals(selected)) {
        Candidate c = entry.copy();
        if (c.origin.equals("catalog")) c.origin = "saved";
        merged.put(c.ip, c);
      }
    }
    return new ArrayList<>(merged.values());
  }

  public static List<Candidate> add(
      List<Candidate> existing, List<Candidate> additions, String origin) {
    Map<String, Candidate> merged = new LinkedHashMap<>();
    for (Candidate c : existing) merged.put(c.ip, c.copy());
    for (Candidate entry : additions) {
      Candidate c = entry.copy();
      c.origin = origin;
      merged.putIfAbsent(c.ip, c);
    }
    return new ArrayList<>(merged.values());
  }

  public static List<Candidate> pending(List<Candidate> nodes, String url) {
    List<Candidate> pending = new ArrayList<>();
    for (Candidate c : nodes) if (!c.testedFor(url)) pending.add(c.copy());
    return pending;
  }
}
