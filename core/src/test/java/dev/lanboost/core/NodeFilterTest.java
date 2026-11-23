package dev.lanboost.core;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class NodeFilterTest {
  private Candidate node(String label) {
    return new Candidate("192.0.2.10", label);
  }

  @Test
  public void combinesCarrierProvinceCityAndKeywords() {
    NodeFilter f = new NodeFilter("电信", "广东", "深圳", "192.0.2 深圳");
    assertTrue(f.matches(node("(中国广东深圳 电信)")));
    assertFalse(f.matches(node("(中国广东深圳 联通)")));
    assertFalse(f.matches(node("(中国广东广州 电信)")));
    assertFalse(f.matches(node("(中国浙江杭州 电信)")));
    assertFalse(new NodeFilter("电信", "广东", "深圳", "missing").matches(node("中国广东深圳 电信")));
  }

  @Test
  public void normalizesFullNamesAndAutonomousRegions() {
    String[][] labels = {
      {"中华人民共和国广东省深圳市 中国电信", "广东", "深圳"},
      {"中国内蒙古自治区呼和浩特市 联通", "内蒙古", "呼和浩特"},
      {"中国新疆维吾尔自治区昌吉回族自治州 电信", "新疆", "昌吉回族自治州"},
      {"中国广西壮族自治区南宁市 移动", "广西", "南宁"},
      {"中国宁夏回族自治区银川市 电信", "宁夏", "银川"},
      {"中国云南红河哈尼族彝族自治州 电信", "云南", "红河哈尼族彝族自治州"},
      {"中国海南澄迈县 移动", "海南", "澄迈县"},
      {"中国台湾台北市", "台湾", "台北"},
      {"中国北京市 电信", "北京", "北京"},
      {"中国香港特别行政区", "香港", "香港"}
    };
    for (String[] entry : labels) {
      NodeFilter.Info i = NodeFilter.info(entry[0]);
      assertEquals(entry[0], entry[1], i.province);
      assertEquals(entry[0], entry[2], i.city);
    }
  }

  @Test
  public void multilineCarriersMatchBothAndProvinceCanStandAlone() {
    Candidate c = node("(中国浙江杭州 电信/联通)");
    assertTrue(new NodeFilter("电信", "浙江", "", "").matches(c));
    assertTrue(new NodeFilter("联通", "", "", "").matches(c));
    assertFalse(new NodeFilter("移动", "浙江", "", "").matches(c));
    assertEquals(new HashSet<>(Arrays.asList("电信", "联通")), NodeFilter.info(c.label).operators);
    assertTrue(NodeFilter.info("中国北京 教育网").operators.contains("教育网"));
  }

  @Test
  public void unknownLabelsNeverGuessLocationOrCarrier() {
    for (String label : Arrays.asList("", "当前 DNS · example.com", "手动导入", "example-host")) {
      NodeFilter.Info i = NodeFilter.info(label);
      assertEquals(NodeFilter.UNKNOWN, i.province);
      assertEquals(NodeFilter.UNKNOWN, i.city);
      assertEquals(Collections.singleton(NodeFilter.UNKNOWN), i.operators);
    }
    assertEquals("未标注", NodeFilter.info("(中国 电信)").province);
    assertTrue(NodeFilter.info("(中国 电信)").operators.contains("电信"));
    assertEquals("境外", NodeFilter.info("(美国加利福尼亚州洛杉矶)").province);
    assertEquals("境外", NodeFilter.info("(新加坡)").province);
  }

  @Test
  public void choicesAreDerivedFromActualCatalogAndCityIsScoped() {
    List<Candidate> nodes =
        Arrays.asList(
            node("广东深圳 电信/联通"),
            node("广东广州 移动"),
            node("浙江杭州 联通"),
            node("广东 电信"),
            node("手动导入"),
            node("日本东京"));
    assertEquals(Arrays.asList("电信", "联通", "移动", "未标注"), NodeFilter.operators(nodes));
    assertEquals(Arrays.asList("浙江", "广东", "境外", "未标注"), NodeFilter.provinces(nodes));
    assertEquals(Arrays.asList("广州", "深圳", "未标注"), NodeFilter.cities(nodes, "广东"));
    assertEquals(Collections.singletonList("杭州"), NodeFilter.cities(nodes, "浙江"));
    assertEquals("", new NodeFilter("", "", "深圳", "").city);
  }

  @Test
  public void selectingMakesCopiesAndKeywordTokensAreCaseInsensitive() {
    Candidate c = node("Example CDN");
    List<Candidate> selected =
        new NodeFilter("", "", "", "cdn EXAMPLE 192.0.2").select(Arrays.asList(c));
    assertEquals(1, selected.size());
    selected.get(0).mbps = 99;
    assertEquals(0, c.mbps, 0);
    assertEquals("全部节点", new NodeFilter(null, null, null, null).summary());
  }

  @Test
  public void batchAndAllUseSameFilterButDifferentDownloadLimits() {
    List<Candidate> nodes = new ArrayList<>();
    for (int i = 1; i <= 30; i++)
      nodes.add(new Candidate("192.0.2." + i, i <= 20 ? "中国广东深圳 电信" : "中国浙江杭州 联通"));
    NodeFilter f = new NodeFilter("电信", "广东", "深圳", "");
    BenchmarkPlan batch =
        new BenchmarkPlan(nodes, f, "http://example.com/file", false, false, 12, 32);
    BenchmarkPlan all = new BenchmarkPlan(nodes, f, "http://example.com/file", false, true, 12, 32);
    assertEquals(20, batch.candidates.size());
    assertEquals(20, all.candidates.size());
    assertEquals(12, batch.downloadLimit);
    assertEquals(20, all.downloadLimit);
    assertEquals(640L * 1048576, all.maxBytes);
    assertEquals(7, all.downloadsForReachable(7));
    assertEquals(20, all.downloadsForReachable(99));
  }

  @Test
  public void continuationSkipsOnlyAttemptsForCurrentUrlWithinFilter() {
    Candidate old = node("广东深圳 电信");
    old.throughputAttempted = true;
    old.testedUrl = "http://example.com/a";
    old.mbps = 123;
    Candidate fresh = new Candidate("192.0.2.11", "广东深圳 电信");
    List<Candidate> nodes = Arrays.asList(old, fresh, node("浙江杭州 联通"));
    NodeFilter f = new NodeFilter("电信", "广东", "", "");
    BenchmarkPlan p = new BenchmarkPlan(nodes, f, old.testedUrl, true, true, 12, 32);
    assertEquals(1, p.candidates.size());
    assertEquals(fresh.ip, p.candidates.get(0).ip);
    assertEquals(123, old.mbps, 0);
    assertEquals(
        2, new BenchmarkPlan(nodes, f, "http://example.com/b", true, true, 12, 32).downloadLimit);
  }

  @Test
  public void trafficBoundUsesLongAndLatencyDoesNotDownload() {
    List<Candidate> nodes = new ArrayList<>();
    for (int i = 0; i < 2048; i++) nodes.add(node("广东深圳 移动"));
    NodeFilter f = new NodeFilter("", "", "", "");
    assertEquals(
        274877906944L,
        new BenchmarkPlan(nodes, f, "http://example.com/a", false, true, 12, 128).maxBytes);
    BenchmarkPlan p = new BenchmarkPlan(nodes, f, "", true, true, 12, 128);
    assertEquals(2048, p.candidates.size());
    assertEquals(0, p.downloadLimit);
    assertEquals(0, p.maxBytes);
  }
}
