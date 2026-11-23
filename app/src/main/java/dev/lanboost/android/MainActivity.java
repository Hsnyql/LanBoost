package dev.lanboost.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import dev.lanboost.core.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import okhttp3.HttpUrl;

public final class MainActivity extends Activity {
  private Ui u;
  private LinearLayout content, nav, rows, profileRow;
  private final List<Button> profileButtons = new ArrayList<>();
  private ScrollView scroll;
  private TextView status,
      ip,
      progress,
      count,
      metricQueries,
      metricMapped,
      metricClients,
      rootStatus,
      serviceDetail,
      uptime;
  private Button runTest, fetch, importer, fastest, runService, nextBatch;
  private TextView catalogTitle, catalogDetail;
  private int nodePage;
  private long renderedNodes = -1;
  private ProgressBar progressBar;
  private Switch ipv4;
  private int page, activityMode;
  private String keyword = "", operator = "", province = "", city = "";
  private final List<Button> filterButtons = new ArrayList<>();
  private Button cityFilter;
  private EditText nodeSearch;
  private long revision = -1;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable tick =
      new Runnable() {
        public void run() {
          refresh();
          handler.postDelayed(this, 800);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    AppState.init(this);
    u = new Ui(this);
    loadFilter();
    if (saved != null) {
      page = saved.getInt("page");
      keyword = saved.getString("keyword", "");
      activityMode = saved.getInt("activityMode");
      nodePage = saved.getInt("nodePage");
    }
    LinearLayout root = u.column();
    root.setBackgroundColor(Ui.BG);
    root.setFocusableInTouchMode(true);
    LinearLayout header = u.row();
    header.setPadding(u.dp(20), u.dp(10), u.dp(12), u.dp(8));
    header.addView(u.image(Ui.NETWORK, Ui.ACCENT, 27));
    TextView brand = u.text("LanBoost", 22, Ui.TEXT, true);
    LinearLayout.LayoutParams brandP = new LinearLayout.LayoutParams(0, -2, 1);
    brandP.setMargins(u.dp(10), 0, 0, 0);
    header.addView(brand, brandP);
    header.addView(u.icon(Ui.HELP, "使用帮助", v -> help()));
    header.addView(u.icon(Ui.SETTINGS, "设置", v -> settings()));
    root.addView(header);
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setVerticalScrollBarEnabled(false);
    content = u.column();
    content.setPadding(u.dp(20), u.dp(12), u.dp(20), u.dp(24));
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    View divider = new View(this);
    divider.setBackgroundColor(Ui.LINE);
    root.addView(divider, new LinearLayout.LayoutParams(-1, u.dp(1)));
    nav = u.row();
    nav.setPadding(u.dp(12), u.dp(8), u.dp(12), u.dp(6));
    root.addView(nav);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()),
                ime = insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            nav.setVisibility(ime.bottom > bars.bottom ? View.GONE : View.VISIBLE);
          } else
            v.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
          return insets;
        });
    setContentView(root);
    root.requestApplyInsets();
    render();
    root.requestFocus();
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 8);
  }

  @Override
  protected void onResume() {
    super.onResume();
    handler.post(tick);
  }

  @Override
  protected void onPause() {
    handler.removeCallbacks(tick);
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putInt("page", page);
    out.putString("keyword", keyword);
    out.putInt("activityMode", activityMode);
    out.putInt("nodePage", nodePage);
    super.onSaveInstanceState(out);
  }

  private void go(int p) {
    page = p;
    render();
    scroll.scrollTo(0, 0);
  }

  private void render() {
    content.removeAllViews();
    rows = null;
    profileRow = null;
    profileButtons.clear();
    filterButtons.clear();
    cityFilter = null;
    nodeSearch = null;
    status =
        ip =
            progress =
                count =
                    metricQueries =
                        metricMapped = metricClients = rootStatus = serviceDetail = uptime = null;
    runTest = fetch = importer = fastest = runService = nextBatch = null;
    catalogTitle = catalogDetail = null;
    renderedNodes = -1;
    ipv4 = null;
    progressBar = null;
    revision = -1;
    nav.removeAllViews();
    String[] names = {"节点优选", "DNS 服务", "活动"};
    int[] icons = {Ui.SPEED, Ui.SERVER, Ui.ACTIVITY};
    for (int i = 0; i < 3; i++) {
      final int n = i;
      LinearLayout item = u.column();
      item.setGravity(Gravity.CENTER);
      item.setPadding(0, u.dp(8), 0, u.dp(8));
      item.setBackground(u.ripple(i == page ? 0xff223725 : Color.TRANSPARENT, 16, 0));
      item.addView(u.image(icons[i], i == page ? Ui.ACCENT : Ui.MUTED, 23));
      u.gap(item, 5);
      TextView navLabel = u.text(names[i], 11, i == page ? Ui.ACCENT : Ui.MUTED, i == page);
      navLabel.setGravity(Gravity.CENTER);
      item.addView(navLabel);
      item.setContentDescription(names[i]);
      item.setOnClickListener(v -> go(n));
      item.setFocusable(true);
      nav.addView(item, new LinearLayout.LayoutParams(0, u.dp(64), 1));
    }
    if (page == 0) speedPage();
    else if (page == 1) servicePage();
    else activityPage();
    refresh();
  }

  private void heading(String eyebrow, String title, String description) {
    content.addView(u.text(eyebrow, 10, Ui.ACCENT, true));
    u.gap(content, 9);
    content.addView(u.text(title, 25, Ui.TEXT, true));
    u.gap(content, 8);
    content.addView(u.text(description, 12, Ui.MUTED, false));
    u.gap(content, 22);
  }

  private void card(View v) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, 0, 0, u.dp(14));
    content.addView(v, p);
  }

  private String localIp() {
    try {
      return LanNetwork.find(this).ip;
    } catch (IOException e) {
      return "未连接 Wi-Fi";
    }
  }

  private String testUrl() {
    return AppState.prefs(this).getString("url." + AppState.profile.name(), AppState.profile.url);
  }

  private void speedPage() {
    heading(
        "BETTER ROUTES. FASTER DOWNLOADS.",
        "找到更快的下载节点",
        "Xbox、Switch、PlayStation 与 PC 平台，分别优选下载节点。");
    LinearLayout summary = u.card();
    summary.setBackground(u.ripple(Ui.CARD, 22, Ui.LINE));
    summary.setOnClickListener(v -> go(1));
    LinearLayout top = u.row();
    status = u.pill("DNS 未启动", false);
    top.addView(status);
    space(top);
    top.addView(u.image(Ui.ARROW, Ui.ACCENT, 19));
    summary.addView(top);
    u.gap(summary, 16);
    ip = u.mono(localIp(), 23, Ui.TEXT);
    summary.addView(ip);
    u.gap(summary, 7);
    summary.addView(u.text("手机地址 · 用作主机或电脑的 DNS", 11, Ui.MUTED, false));
    card(summary);
    LinearLayout title = u.row();
    title.addView(u.text("测速分组", 16, Ui.TEXT, true));
    space(title);
    title.addView(u.pill(Profile.values().length + " 组独立规则", false));
    content.addView(title);
    u.gap(content, 12);
    profileRow = u.column();
    LinearLayout profileLine = null;
    for (Profile p : Profile.values()) {
      if (p.ordinal() % 3 == 0) {
        if (p.ordinal() > 0) u.gap(profileRow, 8);
        profileLine = u.row();
        profileRow.addView(profileLine);
      }
      Button b =
          u.button(
              p.shortLabel(),
              -1,
              p == AppState.profile,
              v -> {
                if (!AppState.busy && !AppState.testing) {
                  nodePage = 0;
                  AppState.switchProfile(this, p);
                  loadFilter();
                  render();
                }
              });
      b.setTextSize(13);
      b.setPadding(u.dp(4), 0, u.dp(4), 0);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, u.dp(48), 1);
      if (p.ordinal() % 3 > 0) lp.setMargins(u.dp(7), 0, 0, 0);
      profileLine.addView(b, lp);
      profileButtons.add(b);
    }
    content.addView(profileRow);
    u.gap(content, 12);
    content.addView(u.text(AppState.profile.description(), 11, Ui.MUTED, false));
    if (AppState.profile == Profile.NS && !testUrl().isEmpty()) {
      u.gap(content, 6);
      content.addView(u.text("测速返回 403 时，可在设置更换任天堂下载链接，或清空链接只测延迟。", 10, Ui.AMBER, false));
    }
    if (!Rules.blockedDomains(AppState.profile).isEmpty()) {
      u.gap(content, 6);
      content.addView(u.text("选用后默认屏蔽本组备用下载域名；可在 DNS 规则详情中关闭。", 10, Ui.MUTED, false));
    }
    u.gap(content, 6);
    String selected = AppState.selected(this, AppState.profile);
    content.addView(
        u.text(
            selected.isEmpty() ? "当前：自动解析" : "已选用 " + selected,
            12,
            selected.isEmpty() ? Ui.MUTED : Ui.ACCENT,
            false));
    u.gap(content, 16);
    LinearLayout actions = u.row();
    fetch = u.button("获取节点", Ui.REFRESH, false, v -> send(BoostService.FETCH));
    importer = u.button("导入", Ui.IMPORT, false, v -> importDialog());
    weighted(actions, fetch, 1.3f, 0);
    weighted(actions, importer, 1, 8);
    content.addView(actions);
    u.gap(content, 10);
    LinearLayout catalogCard = u.card();
    catalogTitle = u.text("", 13, Ui.TEXT, true);
    catalogDetail = u.text("", 11, Ui.MUTED, false);
    catalogCard.addView(catalogTitle);
    u.gap(catalogCard, 6);
    catalogCard.addView(catalogDetail);
    u.gap(catalogCard, 8);
    catalogCard.addView(u.button("获取详情", -1, false, v -> catalogDetails()));
    card(catalogCard);
    LinearLayout filters = u.card();
    LinearLayout filterTitle = u.row();
    filterTitle.addView(u.text("筛选节点", 15, Ui.TEXT, true));
    space(filterTitle);
    Button clear =
        u.button(
            "清空筛选",
            -1,
            false,
            v -> {
              operator = province = city = keyword = "";
              filterChanged();
            });
    clear.setTextSize(12);
    filterButtons.add(clear);
    filterTitle.addView(clear);
    filters.addView(filterTitle);
    u.gap(filters, 10);
    filters.addView(filterButton("运营商", operator, 0));
    u.gap(filters, 8);
    LinearLayout regions = u.row();
    weighted(regions, filterButton("省份 / 地区", province, 1), 1, 0);
    cityFilter = filterButton("城市", city, 2);
    weighted(regions, cityFilter, 1, 8);
    filters.addView(regions);
    u.gap(filters, 10);
    filters.addView(u.text("按节点库标签筛选；无标签的节点归为“未标注”。", 10, Ui.MUTED, false));
    u.gap(filters, 10);
    EditText search = u.input("关键词或 IP · 空格分隔", keyword, false);
    nodeSearch = search;
    Ui.Glyph magnify = new Ui.Glyph(Ui.SEARCH, Ui.MUTED);
    magnify.setBounds(0, 0, u.dp(18), u.dp(18));
    search.setCompoundDrawables(magnify, null, null, null);
    search.setCompoundDrawablePadding(u.dp(10));
    search.setContentDescription("筛选节点");
    filters.addView(search);
    card(filters);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            keyword = s.toString();
            nodePage = 0;
            saveFilter();
            renderNodes();
            refresh();
          }

          public void afterTextChanged(Editable e) {}
        });
    u.gap(content, 12);
    runTest =
        u.button(
            testUrl().isEmpty() ? "测试连通性" : "开始测速",
            Ui.PLAY,
            true,
            v -> {
              if (AppState.testing) send(BoostService.CANCEL);
              else confirmTest(false);
            });
    content.addView(runTest, new LinearLayout.LayoutParams(-1, u.dp(52)));
    if (!testUrl().isEmpty()) {
      u.gap(content, 8);
      nextBatch = u.button("继续测试下一批", Ui.PLAY, false, v -> confirmTest(true));
      content.addView(nextBatch, new LinearLayout.LayoutParams(-1, u.dp(48)));
    }
    progress = u.text("", 11, Ui.MUTED, false);
    u.gap(content, 12);
    content.addView(progress);
    u.gap(content, 8);
    progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progressBar.setIndeterminate(true);
    progressBar.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
    content.addView(progressBar, new LinearLayout.LayoutParams(-1, u.dp(3)));
    u.gap(content, 16);
    if (testUrl().isEmpty()) {
      LinearLayout note = u.card();
      note.setPadding(u.dp(14), u.dp(12), u.dp(14), u.dp(12));
      note.addView(u.text("当前只测试连接延迟", 12, Ui.AMBER, true));
      u.gap(note, 6);
      note.addView(u.text("未设置测速文件。可在设置中填入本组有效下载链接，或先按延迟选择节点；延迟不代表下载速度。", 11, Ui.MUTED, false));
      card(note);
    }
    LinearLayout results = u.row();
    count = u.text("节点", 17, Ui.TEXT, true);
    results.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
    fastest = u.button(testUrl().isEmpty() ? "采用低延迟" : "采用最快", -1, false, v -> selectFastest());
    fastest.setTextSize(12);
    fastest.setPadding(u.dp(12), 0, u.dp(12), 0);
    results.addView(fastest, new LinearLayout.LayoutParams(-2, u.dp(48)));
    content.addView(results);
    u.gap(content, 12);
    rows = u.column();
    content.addView(rows);
  }

  private void renderNodes() {
    if (rows == null || page != 0) return;
    rows.removeAllViews();
    List<Candidate> list = AppState.snapshot();
    int total = list.size();
    renderedNodes = AppState.nodeRevision;
    updateCatalog();
    list = nodeFilter().select(list);
    list.sort(
        Comparator.comparingDouble((Candidate c) -> c.mbps)
            .reversed()
            .thenComparingLong(c -> c.latencyMs < 0 ? Long.MAX_VALUE : c.latencyMs));
    int attempted = (int) list.stream().filter(c -> c.testedFor(testUrl())).count();
    count.setText("节点 " + total + " · 筛后 " + list.size());
    count.setTextSize(13);
    TextView testedCount =
        u.text(
            testUrl().isEmpty()
                ? "已测延迟 " + list.stream().filter(c -> c.latencyMs >= 0).count()
                : "当前链接已测下载 " + attempted + " / " + list.size(),
            11,
            Ui.MUTED,
            false);
    rows.addView(testedCount);
    u.gap(rows, 10);
    if (list.isEmpty()) {
      empty(
          rows,
          total == 0 ? "等待你的第一组节点" : "当前筛选无匹配节点",
          total == 0 ? "获取在线节点，或导入已有 IP 列表。" : "换一个运营商或地区，或清空筛选查看全部节点。",
          Ui.SPEED);
      return;
    }
    String selected = AppState.selected(this, AppState.profile);
    nodePage = Math.min(nodePage, (list.size() - 1) / 60);
    int start = nodePage * 60;
    int index = start;
    for (Candidate c : list.subList(start, Math.min(start + 60, list.size()))) {
      LinearLayout item = u.card();
      boolean chosen = c.ip.equals(selected);
      item.setBackground(u.ripple(chosen ? 0xff203721 : Ui.CARD, 17, chosen ? Ui.ACCENT : Ui.LINE));
      item.setPadding(u.dp(15), u.dp(15), u.dp(15), u.dp(15));
      LinearLayout first = u.row();
      first.addView(
          u.text(String.format(Locale.ROOT, "%02d", ++index), 11, Ui.MUTED, true),
          new LinearLayout.LayoutParams(u.dp(29), -2));
      first.addView(u.mono(c.ip, 14, Ui.TEXT), new LinearLayout.LayoutParams(0, -2, 1));
      if (chosen) first.addView(u.image(Ui.CHECK, Ui.ACCENT, 18));
      item.addView(first);
      u.gap(item, 9);
      if (!c.label.isEmpty()) {
        TextView label = u.text(c.label, 10, Ui.MUTED, false);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(label);
        u.gap(item, 10);
      }
      LinearLayout result = u.row();
      result.addView(
          u.text(
              c.mbps > 0
                  ? String.format(Locale.ROOT, "%.1f", c.mbps)
                  : c.latencyMs >= 0 ? Long.toString(c.latencyMs) : "—",
              23,
              c.mbps > 0 ? Ui.ACCENT : Ui.TEXT,
              true));
      TextView unit =
          u.text(c.mbps > 0 ? "Mbps" : c.latencyMs >= 0 ? "ms" : "", 10, Ui.MUTED, false);
      LinearLayout.LayoutParams unitP = new LinearLayout.LayoutParams(-2, -2);
      unitP.setMargins(u.dp(6), u.dp(9), 0, 0);
      result.addView(unit, unitP);
      TextView detail = u.text(c.status, 10, c.status.contains("失败") ? Ui.AMBER : Ui.MUTED, false);
      item.addView(result);
      u.gap(item, 7);
      item.addView(detail);
      String origin =
          c.origin.equals("catalog")
              ? "完整库"
              : c.origin.equals("dns") ? "DNS 候选" : c.origin.equals("manual") ? "手动导入" : "已保存";
      item.addView(
          u.text(
              origin + (c.measuredAt > 0 ? " · 测速 " + stamp(c.measuredAt) : ""),
              10,
              Ui.MUTED,
              false));
      item.setContentDescription("选用 " + c.ip);
      item.setOnClickListener(v -> choose(c.ip));
      rowCard(rows, item);
    }
    if (list.size() > 60) {
      rows.addView(
          u.text(
              "第 " + (nodePage + 1) + " / " + ((list.size() + 59) / 60) + " 页 · 每页最多 60 项",
              11,
              Ui.MUTED,
              false));
      LinearLayout pages = u.row();
      Button previous =
          u.button(
              "上一页",
              -1,
              false,
              v -> {
                nodePage--;
                renderNodes();
                scrollToNodes();
              });
      Button next =
          u.button(
              "下一页",
              -1,
              false,
              v -> {
                nodePage++;
                renderNodes();
                scrollToNodes();
              });
      u.enabled(previous, nodePage > 0);
      u.enabled(next, start + 60 < list.size());
      weighted(pages, previous, 1, 0);
      weighted(pages, next, 1, 8);
      rows.addView(pages);
    }
  }

  private String stamp(long time) {
    return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(time));
  }

  private void scrollToNodes() {
    scroll.post(() -> scroll.smoothScrollTo(0, rows.getTop()));
  }

  private void updateCatalog() {
    if (catalogTitle == null) return;
    Profile p = AppState.profile;
    android.content.SharedPreferences prefs = AppState.prefs(this);
    int full = NodeStore.catalog(this, p).size();
    long time = prefs.getLong("catalogTime." + p.name(), 0);
    String error = NodeStore.error(this, p);
    long dns = AppState.snapshot().stream().filter(c -> c.origin.equals("dns")).count();
    catalogTitle.setText(
        full > 0
            ? (error.isEmpty() ? "完整节点库 · " : "使用缓存 · ") + full + " 个"
            : dns > 0 ? "DNS 临时候选 · " + dns + " 个" : "尚未获取完整节点库");
    catalogTitle.setTextColor(error.isEmpty() && full > 0 ? Ui.ACCENT : Ui.AMBER);
    catalogDetail.setText(
        full > 0
            ? prefs.getString("catalogSource." + p.name(), "官方节点库")
                + " · 更新于 "
                + stamp(time)
                + (error.isEmpty() ? "\n刷新会保留已有测速结果与选用节点。" : "\n更新失败，完整缓存仍可使用；点击获取节点重试。")
            : error.isEmpty() ? "这些是已保存的候选；获取完整库可扩大选择范围。" : "完整库获取失败，原有节点已保留；点击获取节点重试。");
  }

  private void catalogDetails() {
    Profile p = AppState.profile;
    android.content.SharedPreferences prefs = AppState.prefs(this);
    String error = NodeStore.error(this, p);
    long attempted = prefs.getLong("fetchAttempt." + p.name(), 0);
    TextView message =
        u.text(
            "节点库来自 skydevil88/XboxDownload。按 GitHub Raw → GitHub API 获取，瞬时网络错误最多额外重试一次。\n\n"
                + "获取列表跟随系统当前网络；测速和 DNS 服务使用 Wi-Fi。\n\n"
                + (attempted > 0 ? "最近尝试：" + stamp(attempted) + "\n" : "尚无本版本的获取记录。\n")
                + (error.isEmpty() ? "没有记录到获取错误。" : error),
            13,
            Ui.TEXT,
            false);
    message.setTextIsSelectable(true);
    message.setPadding(u.dp(20), u.dp(16), u.dp(20), u.dp(16));
    ScrollView body = new ScrollView(this);
    body.addView(message);
    u.dialog(
        new AlertDialog.Builder(this)
            .setTitle("节点库获取详情")
            .setView(body)
            .setPositiveButton("关闭", null)
            .create());
  }

  private void servicePage() {
    heading("YOUR PHONE. YOUR DNS.", "把手机变成 DNS", "同一局域网内，让主机和电脑使用各自选好的下载节点。");
    LinearLayout hero = u.card();
    LinearLayout top = u.row();
    status = u.pill("未启动", false);
    top.addView(status);
    space(top);
    uptime = u.text("", 10, Ui.MUTED, false);
    top.addView(uptime);
    hero.addView(top);
    u.gap(hero, 20);
    hero.addView(u.text("设备的主 DNS", 10, Ui.MUTED, true));
    u.gap(hero, 9);
    LinearLayout address = u.row();
    ip = u.mono(localIp(), 23, Ui.TEXT);
    ip.setTextIsSelectable(true);
    address.addView(ip, new LinearLayout.LayoutParams(0, -2, 1));
    address.addView(u.icon(Ui.COPY, "复制 DNS 地址", v -> copy(localIp())));
    hero.addView(address);
    u.gap(hero, 15);
    runService =
        u.button(
            "启动 DNS 服务",
            Ui.PLAY,
            true,
            v -> send(AppState.serving ? BoostService.STOP : BoostService.START));
    hero.addView(runService, new LinearLayout.LayoutParams(-1, u.dp(52)));
    serviceDetail = u.text("", 11, Ui.MUTED, false);
    u.gap(hero, 10);
    hero.addView(serviceDetail);
    u.divider(hero);
    LinearLayout metrics = u.row();
    metricQueries = metric(metrics, "请求", AppState.queries.get());
    metricMapped = metric(metrics, "规则命中", AppState.mappedQueries.get());
    metricClients = metric(metrics, "客户端", AppState.clientCount());
    hero.addView(metrics);
    card(hero);
    content.addView(
        u.text(
            AppState.httpRedirectActive(this)
                ? "国内 HTTP 换源已开启 · Xbox 国际 HTTP 下载转向 CN1"
                : AppState.httpRedirectEnabled(this)
                    ? "国内 HTTP 换源默认开启 · 选好 CN1 节点后生效"
                    : "国内 HTTP 换源已关闭 · 可在设置中开启",
            11,
            Ui.MUTED,
            false));
    u.gap(content, 12);
    content.addView(u.text("下载域名规则", 17, Ui.TEXT, true));
    u.gap(content, 6);
    content.addView(u.text("各组可以同时生效；留空则跟随上游解析。点击查看域名。", 11, Ui.MUTED, false));
    u.gap(content, 12);
    LinearLayout maps = u.card();
    maps.setPadding(u.dp(15), u.dp(5), u.dp(15), u.dp(5));
    for (Profile p : Profile.values()) {
      LinearLayout row = u.row();
      row.setPadding(0, u.dp(15), 0, u.dp(15));
      TextView badge =
          u.pill(
              p == Profile.AKAMAI ? "CDN" : p.shortLabel(), !AppState.selected(this, p).isEmpty());
      badge.setGravity(Gravity.CENTER);
      row.addView(badge, new LinearLayout.LayoutParams(u.dp(52), -2));
      LinearLayout labels = u.column();
      labels.setPadding(u.dp(12), 0, u.dp(5), 0);
      labels.addView(u.text(p.label.replace("Xbox ", ""), 12, Ui.TEXT, true));
      u.gap(labels, 7);
      String selected = AppState.selected(this, p);
      labels.addView(
          u.mono(
              selected.isEmpty() ? "自动解析" : selected,
              12,
              selected.isEmpty() ? Ui.MUTED : Ui.ACCENT));
      row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(u.image(Ui.CHEVRON, Ui.MUTED, 18));
      row.setBackground(u.ripple(Color.TRANSPARENT, 12, 0));
      row.setOnClickListener(v -> manualSelection(p));
      row.setContentDescription("设置 " + p.label);
      maps.addView(row);
      if (p.ordinal() < Profile.values().length - 1) {
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        maps.addView(line, new LinearLayout.LayoutParams(-1, u.dp(1)));
      }
    }
    card(maps);
    LinearLayout options = u.card();
    option(
        options,
        "上游 DNS",
        AppState.prefs(this).getString("upstream", "223.5.5.5") + "  ·  支持备用解析",
        Ui.SERVER,
        v -> settings());
    u.divider(options);
    LinearLayout toggle = u.row();
    LinearLayout captions = u.column();
    captions.addView(u.text("下载域名优先 IPv4", 13, Ui.TEXT, true));
    u.gap(captions, 6);
    captions.addView(u.text("仅对已指定节点的域名生效", 10, Ui.MUTED, false));
    toggle.addView(captions, new LinearLayout.LayoutParams(0, -2, 1));
    ipv4 = new Switch(this);
    ipv4.setChecked(AppState.prefs(this).getBoolean("ipv4", true));
    ipv4.setContentDescription("下载域名优先 IPv4");
    ipv4.setThumbTintList(
        new ColorStateList(
            new int[][] {{android.R.attr.state_checked}, {}}, new int[] {Ui.ACCENT, Ui.MUTED}));
    ipv4.setOnCheckedChangeListener(
        (v, on) -> AppState.prefs(this).edit().putBoolean("ipv4", on).apply());
    toggle.addView(ipv4);
    options.addView(toggle);
    card(options);
    LinearLayout checks = u.card();
    LinearLayout rootRow = u.row();
    rootRow.addView(u.image(Ui.LOCK, Ui.ACCENT, 21));
    LinearLayout rootLabels = u.column();
    rootLabels.setPadding(u.dp(12), 0, 0, 0);
    rootLabels.addView(u.text("Root 权限", 13, Ui.TEXT, true));
    u.gap(rootLabels, 6);
    rootStatus = u.text(AppState.prefs(this).getString("rootStatus", "尚未检查"), 11, Ui.MUTED, false);
    rootLabels.addView(rootStatus);
    rootRow.addView(rootLabels, new LinearLayout.LayoutParams(0, -2, 1));
    rootRow.addView(u.icon(Ui.REFRESH, "检查 Root", v -> send(BoostService.ROOT)));
    checks.addView(rootRow);
    u.divider(checks);
    option(
        checks,
        "连接诊断",
        "检查 Wi-Fi、上游 DNS 和服务应答",
        Ui.ACTIVITY,
        v -> {
          send(BoostService.DIAG);
          activityMode = 1;
          go(2);
        });
    u.divider(checks);
    option(checks, "清理端口规则", "停止服务并清理本应用规则", Ui.REFRESH, v -> send(BoostService.CLEAN));
    card(checks);
    LinearLayout guide = u.card();
    guide.addView(u.text("接入主机，只需三步", 14, Ui.TEXT, true));
    u.gap(guide, 12);
    step(guide, "01", "手机和 Xbox / NS / PS 连接同一路由器");
    step(guide, "02", "主机网络设置 → 手动 DNS → 手机地址");
    step(guide, "03", "暂停再继续下载，查看“活动”页请求");
    u.gap(guide, 5);
    guide.addView(u.text("PC 平台需设置电脑网卡 DNS；停用前把设备 DNS 恢复自动。", 10, Ui.MUTED, false));
    card(guide);
  }

  private TextView metric(LinearLayout row, String label, long value) {
    LinearLayout col = u.column();
    TextView n = u.text(Long.toString(value), 23, Ui.TEXT, true);
    col.addView(n);
    u.gap(col, 5);
    col.addView(u.text(label, 10, Ui.MUTED, false));
    row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
    return n;
  }

  private void step(LinearLayout card, String n, String text) {
    LinearLayout r = u.row();
    r.setPadding(0, 0, 0, u.dp(10));
    r.addView(u.mono(n, 11, Ui.ACCENT), new LinearLayout.LayoutParams(u.dp(28), -2));
    r.addView(u.text(text, 11, Ui.TEXT, false), new LinearLayout.LayoutParams(0, -2, 1));
    card.addView(r);
  }

  private void activityPage() {
    heading("EVERY QUERY, IN VIEW.", "网络活动，一目了然", "看看哪些设备连上了，以及下载域名命中了哪条规则。");
    LinearLayout modes = u.row();
    weighted(
        modes,
        u.button(
            "DNS 请求",
            -1,
            activityMode == 0,
            v -> {
              activityMode = 0;
              render();
            }),
        1,
        0);
    weighted(
        modes,
        u.button(
            "运行日志",
            -1,
            activityMode == 1,
            v -> {
              activityMode = 1;
              render();
            }),
        1,
        8);
    content.addView(modes);
    u.gap(content, 16);
    LinearLayout tools = u.row();
    count = u.text("最近记录", 13, Ui.MUTED, false);
    tools.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
    tools.addView(u.icon(Ui.COPY, "复制日志", v -> copy(AppState.logText())));
    tools.addView(
        u.icon(
            Ui.TRASH,
            "清空记录",
            v -> {
              synchronized (AppState.LOCK) {
                AppState.logs.clear();
                AppState.events.clear();
                AppState.revision++;
              }
              refresh();
            }));
    content.addView(tools);
    u.gap(content, 10);
    rows = u.column();
    content.addView(rows);
  }

  private void renderActivity() {
    if (page != 2 || rows == null) return;
    rows.removeAllViews();
    SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
    if (activityMode == 0) {
      List<AppState.QueryEvent> events = AppState.eventSnapshot();
      count.setText("最近 " + events.size() + " 条请求");
      if (events.isEmpty()) {
        empty(rows, "等待设备的第一次请求", "启动 DNS 后，将主机或电脑 DNS 设置为手机地址。", Ui.ACTIVITY);
        return;
      }
      for (AppState.QueryEvent event : events) {
        LinearLayout item = u.card();
        item.setPadding(u.dp(14), u.dp(14), u.dp(14), u.dp(14));
        LinearLayout top = u.row();
        top.addView(u.pill(event.type, !event.target.isEmpty()));
        space(top);
        top.addView(u.text(clock.format(new Date(event.at)), 10, Ui.MUTED, false));
        item.addView(top);
        u.gap(item, 11);
        TextView domain = u.mono(event.domain, 12, Ui.TEXT);
        domain.setTextIsSelectable(true);
        item.addView(domain);
        u.gap(item, 8);
        String detail =
            !event.result.equals("NOERROR")
                ? event.result
                : event.target.isEmpty()
                    ? "上游解析"
                    : event.target.equals("0.0.0.0")
                        ? "已屏蔽 · 备用下载域名"
                        : event.type.equals("A") ? "→ " + event.target : "已抑制 · 优先 IPv4";
        item.addView(
            u.text(
                detail,
                11,
                !event.result.equals("NOERROR")
                    ? Ui.AMBER
                    : !event.target.isEmpty() ? Ui.ACCENT : Ui.MUTED,
                false));
        u.gap(item, 6);
        item.addView(u.text("来自 " + event.client, 10, Ui.MUTED, false));
        rowCard(rows, item);
      }
    } else {
      String logs = AppState.logText();
      if (logs.isEmpty()) {
        empty(rows, "一切从连接开始", "诊断、授权和服务状态会显示在这里。", Ui.SERVER);
        return;
      }
      String[] lines = logs.split("\n");
      count.setText("最近 " + lines.length + " 条日志");
      for (int i = lines.length - 1; i >= Math.max(0, lines.length - 100); i--) {
        LinearLayout item = u.card();
        item.setPadding(u.dp(14), u.dp(12), u.dp(14), u.dp(12));
        TextView line = u.text(lines[i], 11, lines[i].contains("失败") ? Ui.AMBER : Ui.MUTED, false);
        line.setTextIsSelectable(true);
        item.addView(line);
        rowCard(rows, item);
      }
    }
  }

  private void refresh() {
    boolean work = AppState.busy || AppState.testing;
    if (ip != null) updateText(ip, localIp());
    if (status != null) {
      String label = AppState.serving ? "● DNS 运行中" : AppState.busy ? "正在处理" : "○ DNS 未启动";
      if (!label.contentEquals(status.getText())) {
        status.setText(label);
        status.setTextColor(AppState.serving ? Ui.ACCENT : Ui.MUTED);
        status.setBackground(u.shape(AppState.serving ? 0xff263e27 : Ui.RAISED, 20, 0));
      }
    }
    if (runTest != null) {
      updateText(runTest, AppState.testing ? "取消测速" : testUrl().isEmpty() ? "测试连通性" : "开始测速");
      u.enabled(runTest, !AppState.busy || AppState.testing);
      u.enabled(fetch, !work);
      if (nextBatch != null)
        u.enabled(
            nextBatch,
            !work
                && !NodeLists.pending(nodeFilter().select(AppState.snapshot()), testUrl())
                    .isEmpty());
      u.enabled(importer, !work);
      u.enabled(fastest, !work && !AppState.serving);
      for (Button profileButton : profileButtons) u.enabled(profileButton, !work);
      for (Button button : filterButtons)
        u.enabled(button, !work && (button != cityFilter || !province.isEmpty()));
      if (nodeSearch != null) u.enabled(nodeSearch, !work);
      updateText(progress, AppState.progress);
      progressBar.setVisibility(work ? View.VISIBLE : View.INVISIBLE);
    }
    if (runService != null) {
      String label = AppState.serving ? "停止 DNS 服务" : "启动 DNS 服务";
      if (!label.contentEquals(runService.getText())) {
        runService.setText(label);
        Ui.Glyph serviceIcon = new Ui.Glyph(AppState.serving ? Ui.STOP : Ui.PLAY, Ui.BG);
        serviceIcon.setBounds(0, 0, u.dp(18), u.dp(18));
        runService.setCompoundDrawables(serviceIcon, null, null, null);
      }
      updateText(serviceDetail, AppState.serviceStatus);
      serviceDetail.setTextColor(
          AppState.serviceStatus.contains("失败") || AppState.serviceStatus.contains("待清理")
              ? Ui.AMBER
              : Ui.MUTED);
      u.enabled(runService, !work);
      u.enabled(ipv4, !work && !AppState.serving);
      updateText(metricQueries, Long.toString(AppState.queries.get()));
      updateText(metricMapped, Long.toString(AppState.mappedQueries.get()));
      updateText(metricClients, Integer.toString(AppState.clientCount()));
      updateText(rootStatus, AppState.prefs(this).getString("rootStatus", "尚未检查"));
      updateText(
          uptime,
          AppState.serving
              ? "已运行 " + duration((System.currentTimeMillis() - AppState.serviceStarted) / 1000)
              : "仅作用于局域网");
    }
    if (revision != AppState.revision) {
      revision = AppState.revision;
      if (page == 0 && renderedNodes != AppState.nodeRevision) renderNodes();
      else if (page == 2) renderActivity();
    }
  }

  private void updateText(TextView view, String text) {
    if (!text.contentEquals(view.getText())) view.setText(text);
  }

  private String duration(long seconds) {
    return seconds < 60
        ? seconds + " 秒"
        : seconds < 3600
            ? seconds / 60 + " 分钟"
            : seconds / 3600 + " 小时 " + seconds % 3600 / 60 + " 分钟";
  }

  private void choose(String ip) {
    if (!editable()) return;
    AppState.prefs(this).edit().putString("selected." + AppState.profile.name(), ip).apply();
    AppState.revision++;
    int y = scroll.getScrollY();
    render();
    scroll.post(() -> scroll.scrollTo(0, y));
    toast("已选用 " + ip);
  }

  private void selectFastest() {
    List<Candidate> candidates = nodeFilter().select(AppState.snapshot());
    Optional<Candidate> best =
        testUrl().isEmpty()
            ? candidates.stream()
                .filter(c -> c.latencyMs >= 0)
                .min(Comparator.comparingLong(c -> c.latencyMs))
            : candidates.stream()
                .filter(c -> c.mbps > 0 && c.testedFor(testUrl()))
                .max(Comparator.comparingDouble(c -> c.mbps));
    if (best.isPresent()) choose(best.get().ip);
    else toast(testUrl().isEmpty() ? "请先测试连通性" : "暂无成功的下载测速结果");
  }

  private NodeFilter nodeFilter() {
    return new NodeFilter(operator, province, city, keyword);
  }

  private void loadFilter() {
    SharedPreferences p = AppState.prefs(this);
    String group = AppState.profile.name();
    operator = p.getString("filter.operator." + group, "");
    province = p.getString("filter.province." + group, "");
    city = province.isEmpty() ? "" : p.getString("filter.city." + group, "");
    keyword = p.getString("filter.query." + group, "");
  }

  private void saveFilter() {
    String group = AppState.profile.name();
    AppState.prefs(this)
        .edit()
        .putString("filter.operator." + group, operator)
        .putString("filter.province." + group, province)
        .putString("filter.city." + group, city)
        .putString("filter.query." + group, keyword)
        .apply();
  }

  private void filterChanged() {
    nodePage = 0;
    saveFilter();
    int y = scroll.getScrollY();
    render();
    scroll.post(() -> scroll.scrollTo(0, y));
  }

  private Button filterButton(String title, String value, int kind) {
    String label = title + "：" + (value.isEmpty() ? "全部" : value);
    Button button = u.button(label, Ui.CHEVRON, false, v -> pickFilter(kind));
    button.setTextSize(12);
    button.setPadding(u.dp(10), 0, u.dp(10), 0);
    button.setSingleLine(true);
    button.setEllipsize(TextUtils.TruncateAt.END);
    button.setContentDescription(label);
    filterButtons.add(button);
    return button;
  }

  private void pickFilter(int kind) {
    if (AppState.busy || AppState.testing) return;
    List<Candidate> nodes = AppState.snapshot();
    List<String> values = new ArrayList<>();
    values.add("");
    values.addAll(
        kind == 0
            ? NodeFilter.operators(nodes)
            : kind == 1 ? NodeFilter.provinces(nodes) : NodeFilter.cities(nodes, province));
    String current = kind == 0 ? operator : kind == 1 ? province : city;
    // Keep a saved choice visible even if a refreshed catalog no longer contains it.
    if (!values.contains(current)) values.add(current);
    String[] labels = values.toArray(new String[0]);
    labels[0] = kind == 0 ? "全部运营商" : kind == 1 ? "全部省份 / 地区" : "全部城市";
    u.dialog(
        new AlertDialog.Builder(this)
            .setTitle(kind == 0 ? "选择运营商" : kind == 1 ? "选择省份 / 地区" : province + " · 选择城市")
            .setSingleChoiceItems(
                labels,
                values.indexOf(current),
                (d, which) -> {
                  if (kind == 0) operator = values.get(which);
                  else if (kind == 1) {
                    province = values.get(which);
                    city = "";
                  } else city = values.get(which);
                  d.dismiss();
                  filterChanged();
                })
            .setNegativeButton("取消", null)
            .create());
  }

  private void confirmTest(boolean next) {
    if (AppState.busy || AppState.testing) return;
    List<Candidate> source = AppState.snapshot();
    if (source.isEmpty()) {
      toast("请先获取或导入节点");
      return;
    }
    NodeFilter filter = nodeFilter();
    String url = testUrl();
    int top = AppState.prefs(this).getInt("top", 12),
        cap = AppState.prefs(this).getInt("capMiB", 32);
    BenchmarkPlan batch = new BenchmarkPlan(source, filter, url, next, false, top, cap);
    BenchmarkPlan all = new BenchmarkPlan(source, filter, url, next, true, top, cap);
    if (batch.candidates.isEmpty()) {
      toast("当前筛选下没有待测节点");
      return;
    }
    boolean latency = url.isEmpty();
    LinearLayout box = dialogBox();
    box.addView(u.text(filter.summary(), 13, Ui.ACCENT, true));
    u.gap(box, 10);
    box.addView(
        u.text(
            "范围：" + batch.candidates.size() + " 个" + (next ? "剩余" : "筛选") + "节点",
            13,
            Ui.TEXT,
            true));
    TextView details = u.text("", 12, Ui.MUTED, false);
    RadioGroup modes = new RadioGroup(this);
    if (!latency) {
      RadioButton one = new RadioButton(this), every = new RadioButton(this);
      one.setId(View.generateViewId());
      every.setId(View.generateViewId());
      one.setText(String.format(Locale.ROOT, "本批最多 %d 个", batch.downloadLimit));
      every.setText(
          String.format(Locale.ROOT, "测完%s · %d 个", next ? "剩余" : "筛选结果", all.downloadLimit));
      for (RadioButton button : new RadioButton[] {one, every}) {
        button.setTextColor(Ui.TEXT);
        button.setTextSize(13);
        button.setMinHeight(u.dp(48));
        button.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
        modes.addView(button);
      }
      modes.check(one.getId());
      modes.setOnCheckedChangeListener(
          (group, checked) ->
              details.setText(testDetails(checked == every.getId() ? all : batch, cap)));
      box.addView(modes);
      details.setText(testDetails(batch, cap));
    } else details.setText("只测上述节点的连接延迟，不下载测试文件。延迟较低不一定代表下载更快。");
    u.gap(box, 10);
    box.addView(details);
    ScrollView body = new ScrollView(this);
    body.addView(box);
    u.dialog(
        new AlertDialog.Builder(this)
            .setTitle(latency ? "测试连接延迟" : next ? "继续测速" : "开始下载测速")
            .setView(body)
            .setNegativeButton("取消", null)
            .setPositiveButton(
                "开始",
                (d, w) ->
                    startForegroundService(
                        new Intent(this, BoostService.class)
                            .setAction(BoostService.TEST)
                            .putExtra("filter", filter.query)
                            .putExtra("operator", filter.operator)
                            .putExtra("province", filter.province)
                            .putExtra("city", filter.city)
                            .putExtra("nextBatch", next)
                            .putExtra(
                                "allCandidates",
                                !latency
                                    && modes.getCheckedRadioButtonId()
                                        == modes.getChildAt(1).getId())))
            .create());
  }

  private String testDetails(BenchmarkPlan plan, int cap) {
    double mib = plan.maxBytes / 1048576.0;
    String amount =
        mib >= 1024
            ? String.format(Locale.ROOT, "%.2f GiB", mib / 1024)
            : String.format(Locale.ROOT, "%.0f MiB", mib);
    return "先检查连通性，再按延迟顺序下载测试；不可达节点会跳过。\n每节点最多 "
        + cap
        + " MiB，测试数据总上限 "
        + amount
        + "。"
        + (plan.next ? "\n跳过已尝试当前链接的节点，保留已有结果。" : "")
        + (plan.all ? "\n数量较多时耗时较长，可随时取消，再用下一批继续。" : "")
        + "\n仅通过 Wi-Fi 测速，测试数据不会保存为文件。";
  }

  private void manualSelection(Profile p) {
    if (!editable()) return;
    LinearLayout box = dialogBox();
    box.addView(u.text(p.description(), 12, Ui.MUTED, false));
    u.gap(box, 14);
    EditText value = u.input("填入 IPv4 地址", AppState.selected(this, p), false);
    value.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
    box.addView(value);
    u.gap(box, 10);
    box.addView(u.text("自动解析会清除这组指定 IP，交给上游 DNS。", 11, Ui.MUTED, false));
    CheckBox block = new CheckBox(this);
    if (!Rules.blockedDomains(p).isEmpty()) {
      u.gap(box, 12);
      block.setText("屏蔽备用下载域名");
      block.setTextColor(Ui.TEXT);
      block.setChecked(AppState.prefs(this).getBoolean("blockFallbacks." + p.name(), true));
      box.addView(block);
      box.addView(u.text("沿用原项目的备用 CDN 屏蔽规则，仅在本组选了 IP 时生效。下载异常可关闭。", 11, Ui.MUTED, false));
      u.gap(box, 8);
      box.addView(u.mono(String.join("\n", Rules.blockedDomains(p)), 10, Ui.MUTED));
    }
    u.gap(box, 16);
    box.addView(u.text("本组 DNS 域名", 12, Ui.ACCENT, true));
    u.gap(box, 8);
    box.addView(u.mono(String.join("\n", Rules.domains(p)), 10, Ui.MUTED));
    if (p == Profile.AKAMAI) {
      u.gap(box, 10);
      box.addView(
          u.text(
              "通用规则会影响匹配的非游戏 Akamai 资源；仅在本组选用 IP 后生效。Steam 条目是商店静态资源，不代表 Steam 游戏下载提速。",
              11,
              Ui.MUTED,
              false));
    }
    ScrollView dialogScroll = new ScrollView(this);
    dialogScroll.addView(box);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(p.label)
            .setView(dialogScroll)
            .setNegativeButton("取消", null)
            .setNeutralButton(
                "自动解析",
                (d, w) -> {
                  AppState.prefs(this).edit().remove("selected." + p.name()).apply();
                  render();
                })
            .setPositiveButton("保存", null)
            .create();
    u.dialog(dialog);
    dialog
        .getButton(-1)
        .setOnClickListener(
            v -> {
              String s = value.getText().toString().trim();
              if (!Ipv4.valid(s)) {
                value.setError("请输入有效 IPv4");
                return;
              }
              AppState.prefs(this)
                  .edit()
                  .putString("selected." + p.name(), s)
                  .putBoolean("blockFallbacks." + p.name(), block.isChecked())
                  .apply();
              dialog.dismiss();
              render();
            });
  }

  private void importDialog() {
    LinearLayout box = dialogBox();
    EditText input = u.input("每行一个 IP，可附地区与运营商", "", true);
    input.setMinLines(5);
    box.addView(input);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("导入节点")
            .setView(box)
            .setNegativeButton("取消", null)
            .setNeutralButton(
                "选择文件",
                (d, w) ->
                    startActivityForResult(
                        new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .setType("text/*")
                            .addCategory(Intent.CATEGORY_OPENABLE),
                        11))
            .setPositiveButton("导入", null)
            .create();
    u.dialog(dialog);
    dialog
        .getButton(-1)
        .setOnClickListener(
            v -> {
              if (importText(input.getText().toString())) dialog.dismiss();
            });
  }

  private boolean importText(String text) {
    if (AppState.busy || AppState.testing) {
      toast("请等待当前任务完成");
      return false;
    }
    List<Candidate> loaded = Candidate.parse(text);
    if (loaded.isEmpty()) {
      toast("没有找到有效 IPv4 地址");
      return false;
    }
    int before = AppState.snapshot().size();
    NodeStore.add(this, AppState.profile, loaded, "manual");
    AppState.reloadNodes(this);
    int added = AppState.snapshot().size() - before;
    AppState.progress = "已新增 " + added + " 个节点";
    refresh();
    return true;
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != 11 || result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    Profile target = AppState.profile;
    new Thread(
            () -> {
              try (InputStream in = getContentResolver().openInputStream(uri);
                  ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (in == null) throw new IOException("文件无法读取");
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) != -1) {
                  out.write(b, 0, n);
                  if (out.size() > 1048576) throw new IOException("文件超过 1 MiB");
                }
                String value = out.toString("UTF-8");
                runOnUiThread(
                    () -> {
                      if (AppState.profile != target) {
                        toast("分组已变化，请重新导入");
                        return;
                      }
                      importText(value);
                    });
              } catch (Exception e) {
                runOnUiThread(() -> toast(e.getMessage()));
              }
            },
            "import")
        .start();
  }

  private void settings() {
    if (!editable()) return;
    SharedPreferences p = AppState.prefs(this);
    LinearLayout box = dialogBox();
    box.addView(u.text("DNS 与测速设置", 12, Ui.ACCENT, true));
    EditText upstream = field(box, "主 DNS", p.getString("upstream", "223.5.5.5"), "IPv4 地址", false),
        backup = field(box, "备用 DNS", p.getString("backup", "119.29.29.29"), "可留空", false);
    EditText url =
        field(box, AppState.profile.shortLabel() + " 分组测速链接", testUrl(), "留空时只测连接延迟", true);
    url.setMaxLines(4);
    u.gap(box, 6);
    box.addView(u.text("需使用该组域名的有效 HTTP/HTTPS 文件链接；应用链接可能过期。", 10, Ui.MUTED, false));
    EditText seconds = field(box, "单节点时长 · 2–20 秒", "" + p.getInt("seconds", 5), "", false),
        top = field(box, "每批下载测速节点数 · 1–50", "" + p.getInt("top", 12), "", false),
        cap = field(box, "单节点流量上限 · 1–128 MiB", "" + p.getInt("capMiB", 32), "", false);
    seconds.setInputType(2);
    top.setInputType(2);
    cap.setInputType(2);
    EditText custom =
        field(
            box,
            "自定义 DNS · 每行「域名 IP」",
            p.getString("custom", ""),
            "example.com 192.168.2.10",
            true);
    custom.setMinLines(3);
    u.gap(box, 18);
    Switch http = new Switch(this);
    http.setText("国内 HTTP 换源");
    http.setTextColor(Ui.TEXT);
    http.setTextSize(13);
    http.setChecked(AppState.httpRedirectEnabled(this));
    box.addView(http);
    u.gap(box, 8);
    box.addView(
        u.text(
            "默认开启，选好 CN1 节点后生效；未选节点时仍可使用 DNS。仅适用于 Xbox HTTP 下载，相关 HTTPS 下载异常时可关闭。",
            11,
            Ui.MUTED,
            false));
    u.gap(box, 12);
    ScrollView body = new ScrollView(this);
    body.addView(box);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("设置")
            .setView(body)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create();
    u.dialog(dialog);
    dialog
        .getButton(-1)
        .setOnClickListener(
            v -> {
              try {
                String first = upstream.getText().toString().trim(),
                    second = backup.getText().toString().trim(),
                    test = url.getText().toString().trim();
                if (!Ipv4.valid(first) || (!second.isEmpty() && !Ipv4.valid(second)))
                  throw new IllegalArgumentException("DNS 需为有效 IPv4");
                if (first.equals(localIp()) || second.equals(localIp()))
                  throw new IllegalArgumentException("上游 DNS 不能指向本机");
                if (!test.isEmpty()) {
                  HttpUrl parsed = HttpUrl.get(test);
                  if (!parsed.username().isEmpty() || !parsed.password().isEmpty())
                    throw new IllegalArgumentException("测速链接不能包含用户名或密码");
                  test = parsed.toString();
                }
                int s = Integer.parseInt(seconds.getText().toString()),
                    n = Integer.parseInt(top.getText().toString()),
                    m = Integer.parseInt(cap.getText().toString());
                if (s < 2 || s > 20 || n < 1 || n > 50 || m < 1 || m > 128)
                  throw new IllegalArgumentException("测速数值超出范围");
                String rules = custom.getText().toString();
                Rules.parseCustom(rules);
                p.edit()
                    .putString("upstream", first)
                    .putString("backup", second)
                    .putString("url." + AppState.profile.name(), test)
                    .putInt("seconds", s)
                    .putInt("top", n)
                    .putInt("capMiB", m)
                    .putString("custom", rules)
                    .putBoolean("redirect", http.isChecked())
                    .apply();
                dialog.dismiss();
                AppState.log("设置已保存");
                render();
              } catch (Exception e) {
                toast(e.getMessage());
              }
            });
  }

  private void help() {
    u.dialog(
        new AlertDialog.Builder(this)
            .setTitle("开始加速")
            .setMessage(
                "1. 手机和主机 / 电脑连接同一路由器。\n\n"
                    + "2. 选平台、获取节点，按运营商、省份和城市筛选，再测速并选用 IP。可选本批或测完筛选结果。Xbox 四组、NS、PS、EA、战网和其他 CDN"
                    + " 分别保存。NS / PS / EA 使用各自测试文件，空链接只测连接延迟。\n\n"
                    + "3. 在 KernelSU 中给 LanBoost 超级用户权限，再启动 DNS 服务。国内 HTTP 换源默认开启，选好 CN1 节点后自动生效；未选"
                    + " CN1 时仅提供 DNS。\n\n"
                    + "4. Xbox / NS / PS 的网络设置 → 手动 DNS，主 DNS 填手机地址，副 DNS 尽量留空或填同一地址。PC 平台设置电脑网卡"
                    + " DNS。\n\n"
                    + "5. 暂停再继续下载，去活动页查看请求。DNS 选址改善下载路径，不提供游戏联机代理。\n\n"
                    + "离开 Wi-Fi 或停止服务前，请把设备 DNS 恢复自动。\n\n"
                    + "LanBoost "
                    + BuildConfig.VERSION_NAME
                    + " · 测试版")
            .setNeutralButton("开源许可", (d, w) -> licenses())
            .setPositiveButton("知道了", null)
            .create());
  }

  private void licenses() {
    String[] titles = {"项目许可与来源", "GNU GPL v3.0 全文", "第三方组件许可"};
    String[] files = {"NOTICE.md", "LICENSE", "THIRD_PARTY_NOTICES.txt"};
    u.dialog(
        new AlertDialog.Builder(this)
            .setTitle("LanBoost · GPL-3.0-only")
            .setItems(titles, (d, which) -> showLicense(titles[which], files[which]))
            .setNegativeButton("关闭", null)
            .create());
  }

  private void showLicense(String title, String file) {
    try (InputStream input = getAssets().open(file);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int size;
      while ((size = input.read(buffer)) != -1) bytes.write(buffer, 0, size);
      TextView text = u.text(bytes.toString("UTF-8"), 12, Ui.TEXT, false);
      text.setTextIsSelectable(true);
      text.setPadding(u.dp(20), u.dp(12), u.dp(20), u.dp(12));
      ScrollView body = new ScrollView(this);
      body.addView(text);
      u.dialog(
          new AlertDialog.Builder(this)
              .setTitle(title)
              .setView(body)
              .setPositiveButton("关闭", null)
              .create());
    } catch (IOException e) {
      toast("无法读取许可文本");
    }
  }

  private boolean editable() {
    if (AppState.serving || AppState.testing || AppState.busy) {
      toast("请先停止 DNS 服务或测速");
      return false;
    }
    return true;
  }

  private void send(String action) {
    if ((AppState.busy || AppState.testing)
        && !action.equals(BoostService.STOP)
        && !action.equals(BoostService.CANCEL)) {
      toast("任务进行中，请稍候");
      return;
    }
    startForegroundService(new Intent(this, BoostService.class).setAction(action));
  }

  private void option(
      LinearLayout parent, String title, String subtitle, int icon, View.OnClickListener click) {
    LinearLayout row = u.row();
    row.setMinimumHeight(u.dp(48));
    row.setPadding(0, u.dp(3), 0, u.dp(3));
    row.addView(u.image(icon, Ui.ACCENT, 21));
    LinearLayout labels = u.column();
    labels.setPadding(u.dp(12), 0, u.dp(4), 0);
    labels.addView(u.text(title, 13, Ui.TEXT, true));
    u.gap(labels, 6);
    labels.addView(u.text(subtitle, 10, Ui.MUTED, false));
    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(u.image(Ui.CHEVRON, Ui.MUTED, 18));
    row.setOnClickListener(click);
    row.setBackground(u.ripple(Color.TRANSPARENT, 10, 0));
    row.setContentDescription(title);
    parent.addView(row);
  }

  private void empty(LinearLayout parent, String title, String subtitle, int icon) {
    LinearLayout box = u.card();
    box.setGravity(Gravity.CENTER);
    box.setPadding(u.dp(20), u.dp(30), u.dp(20), u.dp(30));
    box.addView(u.image(icon, Ui.MUTED, 34));
    u.gap(box, 16);
    TextView caption = u.text(title, 14, Ui.TEXT, true);
    caption.setGravity(Gravity.CENTER);
    box.addView(caption);
    u.gap(box, 9);
    TextView detail = u.text(subtitle, 11, Ui.MUTED, false);
    detail.setGravity(Gravity.CENTER);
    box.addView(detail);
    parent.addView(box);
  }

  private void rowCard(LinearLayout parent, View item) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, 0, 0, u.dp(9));
    parent.addView(item, p);
  }

  private void space(LinearLayout row) {
    row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
  }

  private void weighted(LinearLayout parent, View child, float weight, int margin) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, u.dp(48), weight);
    p.setMargins(u.dp(margin), 0, 0, 0);
    parent.addView(child, p);
  }

  private LinearLayout dialogBox() {
    LinearLayout box = u.column();
    box.setPadding(u.dp(22), u.dp(8), u.dp(22), u.dp(12));
    return box;
  }

  private EditText field(LinearLayout box, String label, String value, String hint, boolean multi) {
    u.gap(box, 16);
    box.addView(u.text(label, 11, Ui.MUTED, false));
    u.gap(box, 8);
    EditText input = u.input(hint, value, multi);
    box.addView(input);
    return input;
  }

  private void toast(String text) {
    Toast.makeText(this, text == null ? "操作失败" : text, Toast.LENGTH_LONG).show();
  }

  private void copy(String text) {
    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
        .setPrimaryClip(ClipData.newPlainText("LanBoost", text));
    toast("已复制");
  }
}
