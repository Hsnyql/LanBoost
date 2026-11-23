package dev.lanboost.core;

import java.util.*;
import java.util.regex.Pattern;

/** Interpret catalog labels only; never infer a location or carrier from an IP address. */
public final class NodeFilter {
  public static final String UNKNOWN = "未标注", OVERSEAS = "境外";
  private static final String[] PROVINCES = {
    "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江", "上海", "江苏", "浙江", "安徽", "福建", "江西",
    "山东", "河南", "湖北", "湖南", "广东", "广西", "海南", "重庆", "四川", "贵州", "云南", "西藏", "陕西", "甘肃",
    "青海", "宁夏", "新疆", "台湾", "香港", "澳门"
  };
  private static final String[] CARRIERS = {"电信", "联通", "移动", "教育网", "广电"};
  private static final String[] COUNTRIES = {
    "阿根廷",
    "阿联酋",
    "爱尔兰",
    "奥地利",
    "澳大利亚",
    "巴西",
    "保加利亚",
    "比利时",
    "波兰",
    "丹麦",
    "德国",
    "法国",
    "哥伦比亚",
    "哥斯达黎加",
    "韩国",
    "荷兰",
    "加拿大",
    "捷克",
    "肯尼亚",
    "罗马尼亚",
    "马来西亚",
    "美国",
    "秘鲁",
    "缅甸",
    "墨西哥",
    "南非",
    "挪威",
    "葡萄牙",
    "日本",
    "瑞典",
    "瑞士",
    "俄罗斯",
    "特立尼达和多巴哥",
    "土耳其",
    "乌克兰",
    "西班牙",
    "希腊",
    "新加坡",
    "新西兰",
    "以色列",
    "意大利",
    "印度尼西亚",
    "印度",
    "英国",
    "智利",
    "泰国",
    "越南",
    "菲律宾",
    "芬兰",
    "冰岛"
  };
  private static final Pattern HAN = Pattern.compile("[\\p{IsHan}·]+"),
      SEPARATORS = Pattern.compile("[\\s()（）/／,，|·-]+"),
      TERMS = Pattern.compile("中国电信|中国联通|中国移动|中国广电|电信|联通|移动|教育网|教育|广电");
  public final String operator, province, city, query;

  public NodeFilter(String operator, String province, String city, String query) {
    this.operator = clean(operator);
    this.province = clean(province);
    this.city = this.province.isEmpty() ? "" : clean(city);
    this.query = clean(query);
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  public static final class Info {
    public final Set<String> operators;
    public final String province, city;

    private Info(Set<String> operators, String province, String city) {
      this.operators = Collections.unmodifiableSet(new LinkedHashSet<>(operators));
      this.province = province;
      this.city = city;
    }
  }

  public static Info info(String label) {
    String text = clean(label);
    Set<String> operators = new LinkedHashSet<>();
    if (text.isEmpty() || text.startsWith("当前 DNS") || text.equals("手动导入"))
      return new Info(Collections.singleton(UNKNOWN), UNKNOWN, UNKNOWN);
    for (String carrier : CARRIERS)
      if (text.contains(carrier) || carrier.equals("教育网") && text.contains("教育"))
        operators.add(carrier);
    if (operators.isEmpty()) operators.add(UNKNOWN);
    String geo = SEPARATORS.matcher(TERMS.matcher(text).replaceAll("")).replaceAll("");
    if (geo.startsWith("中华人民共和国")) geo = geo.substring(7);
    else if (geo.startsWith("中国")) geo = geo.substring(2);
    for (String province : PROVINCES) {
      if (!geo.startsWith(province)) continue;
      String rest = geo.substring(province.length());
      for (String suffix : new String[] {"维吾尔自治区", "壮族自治区", "回族自治区", "特别行政区", "自治区", "省"})
        if (rest.startsWith(suffix)) {
          rest = rest.substring(suffix.length());
          break;
        }
      if (Arrays.asList("北京", "天津", "上海", "重庆", "香港", "澳门").contains(province))
        return new Info(operators, province, province);
      if (rest.endsWith("市")) rest = rest.substring(0, rest.length() - 1);
      String city = !rest.isEmpty() && HAN.matcher(rest).matches() ? rest : UNKNOWN;
      return new Info(operators, province, city);
    }
    for (String country : COUNTRIES)
      if (geo.startsWith(country)) return new Info(operators, OVERSEAS, UNKNOWN);
    return new Info(operators, UNKNOWN, UNKNOWN);
  }

  public boolean matches(Candidate candidate) {
    Info i = info(candidate.label);
    if (!operator.isEmpty() && !i.operators.contains(operator)) return false;
    if (!province.isEmpty() && !province.equals(i.province)) return false;
    if (!city.isEmpty() && !city.equals(i.city)) return false;
    String haystack = (candidate.ip + " " + candidate.label).toLowerCase(Locale.ROOT);
    for (String word : query.toLowerCase(Locale.ROOT).split("\\s+"))
      if (!haystack.contains(word)) return false;
    return true;
  }

  public List<Candidate> select(List<Candidate> candidates) {
    List<Candidate> result = new ArrayList<>();
    for (Candidate c : candidates) if (matches(c)) result.add(c.copy());
    return result;
  }

  public static List<String> operators(List<Candidate> candidates) {
    Set<String> present = new HashSet<>();
    for (Candidate c : candidates) present.addAll(info(c.label).operators);
    List<String> result = new ArrayList<>();
    for (String carrier : CARRIERS) if (present.contains(carrier)) result.add(carrier);
    if (present.contains(UNKNOWN)) result.add(UNKNOWN);
    return result;
  }

  public static List<String> provinces(List<Candidate> candidates) {
    Set<String> present = new HashSet<>();
    for (Candidate c : candidates) present.add(info(c.label).province);
    List<String> result = new ArrayList<>();
    for (String p : PROVINCES) if (present.contains(p)) result.add(p);
    if (present.contains(OVERSEAS)) result.add(OVERSEAS);
    if (present.contains(UNKNOWN)) result.add(UNKNOWN);
    return result;
  }

  public static List<String> cities(List<Candidate> candidates, String province) {
    Set<String> cities = new TreeSet<>();
    for (Candidate c : candidates) {
      Info i = info(c.label);
      if (i.province.equals(province)) cities.add(i.city);
    }
    boolean unknown = cities.remove(UNKNOWN);
    List<String> result = new ArrayList<>(cities);
    if (unknown) result.add(UNKNOWN);
    return result;
  }

  public boolean active() {
    return !operator.isEmpty() || !province.isEmpty() || !city.isEmpty() || !query.isEmpty();
  }

  public String summary() {
    List<String> parts = new ArrayList<>();
    if (!operator.isEmpty()) parts.add(operator);
    if (!province.isEmpty()) parts.add(province);
    if (!city.isEmpty() && !city.equals(province)) parts.add(city);
    if (!query.isEmpty()) parts.add("关键词：" + query);
    return parts.isEmpty() ? "全部节点" : String.join(" · ", parts);
  }
}
