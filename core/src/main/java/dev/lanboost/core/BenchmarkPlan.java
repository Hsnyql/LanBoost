package dev.lanboost.core;

import java.util.*;

/** Shared by the confirmation UI and service so displayed scope/limits match actual work. */
public final class BenchmarkPlan {
  public final List<Candidate> candidates;
  public final int downloadLimit;
  public final long maxBytes;
  public final boolean all, next;
  public final String summary;

  public BenchmarkPlan(
      List<Candidate> source,
      NodeFilter filter,
      String url,
      boolean next,
      boolean all,
      int batchSize,
      int capMiB) {
    this.all = all;
    this.next = next;
    this.summary = filter.summary();
    List<Candidate> chosen = filter.select(source);
    if (next && !url.isEmpty()) chosen = NodeLists.pending(chosen, url);
    candidates = Collections.unmodifiableList(chosen);
    downloadLimit =
        url.isEmpty()
            ? 0
            : Math.min(chosen.size(), all ? chosen.size() : Math.max(1, Math.min(50, batchSize)));
    maxBytes = (long) downloadLimit * Math.max(1, Math.min(128, capMiB)) * 1_048_576L;
  }

  public int downloadsForReachable(int reachable) {
    return Math.min(downloadLimit, Math.max(0, reachable));
  }
}
