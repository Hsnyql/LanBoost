package dev.lanboost.core;

public enum Profile {
  CN1("Xbox CN1 · 主下载", "XboxCn1", "http://assets1.xboxlive.cn/Z/routing/extraextralarge.txt"),
  CN2(
      "Xbox CN2 · 旧游戏",
      "XboxCn2",
      "http://dlassets.xboxlive.cn/public/content/1d6640d3-3441-42bd-bffd-953d7d09ff5c/26213de4-885d-4eaa-a433-ed5157116507/1.2.1.0.89417ea8-51b5-408c-9283-60c181763a39/Microsoft.Max_1.2.1.0_neutral__ph1m9x8skttmg"),
  GLOBAL("Xbox COM · Akamai", "Akamai", "http://xvcf1.xboxlive.com/Z/routing/extraextralarge.txt"),
  APP("Xbox 应用 · 微软商店", "XboxApp", ""),
  NS("NS · Nintendo Switch", "Akamai", "http://ctest-dl-lp1.cdn.nintendo.net/30m"),
  PS("PS · PlayStation", "Ps", "http://gst.prod.dl.playstation.net/networktest/get_192m"),
  EA(
      "EA · Electronic Arts",
      "Akamai",
      "http://origin-a.akamaihd.net/EA-Desktop-Client-Download/installer-releases/EA%20app.dmg"),
  BATTLE("战网 · 国际服", "Akamai", ""),
  AKAMAI("其他平台 · Akamai CDN", "Akamai", "");

  public String shortLabel() {
    return switch (this) {
      case CN1 -> "CN1";
      case CN2 -> "CN2";
      case GLOBAL -> "COM";
      case APP -> "应用";
      case NS -> "NS";
      case PS -> "PS";
      case EA -> "EA";
      case BATTLE -> "战网";
      case AKAMAI -> "其他 CDN";
    };
  }

  public String description() {
    return switch (this) {
      case CN1 -> "主游戏下载 · assets1.xboxlive.cn";
      case CN2 -> "部分旧游戏下载 · dlassets.xboxlive.cn";
      case GLOBAL -> "国际游戏下载 · xvcf1.xboxlive.com";
      case APP -> "应用及部分 PC 游戏 · dl.delivery.mp.microsoft.com";
      case NS -> "Switch 内容与更新 · 使用任天堂测试文件";
      case PS -> "PlayStation 下载 · 使用 PS 独立节点库与测试文件";
      case EA -> "EA 下载 · 使用 EA 安装文件测试 Akamai 节点";
      case BATTLE -> "战网国际服 · 填入有效下载链接可测吞吐";
      case AKAMAI -> "Epic、育碧、Riot 等 Akamai 资源 · 按实际域名测试";
    };
  }

  public String defaultHost() {
    return switch (this) {
      case CN1 -> "assets1.xboxlive.cn";
      case CN2 -> "dlassets.xboxlive.cn";
      case GLOBAL -> "xvcf1.xboxlive.com";
      case APP -> "dl.delivery.mp.microsoft.com";
      case NS -> "ctest-dl-lp1.cdn.nintendo.net";
      case PS -> "gst.prod.dl.playstation.net";
      case EA -> "origin-a.akamaihd.net";
      case BATTLE -> "blzddist1-a.akamaihd.net";
      case AKAMAI -> "epicgames-download1.akamaized.net";
    };
  }

  public final String label, listKey, url;

  Profile(String label, String listKey, String url) {
    this.label = label;
    this.listKey = listKey;
    this.url = url;
  }

  @Override
  public String toString() {
    return label;
  }
}
