package dev.lanboost.core;

public final class RootRules {
  public static final String CHAIN = "LANBOOST_IN";

  private RootRules() {}

  public static String cleanup() {
    return "while iptables -w 3 -t nat -C PREROUTING -j "
        + CHAIN
        + " 2>/dev/null; do iptables -w 3 -t nat -D PREROUTING -j "
        + CHAIN
        + " || exit 1; done\n"
        + "if iptables -w 3 -t nat -L "
        + CHAIN
        + " -n >/dev/null 2>&1; then iptables -w 3 -t nat -F "
        + CHAIN
        + " && iptables -w 3 -t nat -X "
        + CHAIN
        + " || exit 1; fi\n";
  }

  public static String install(String iface, String ip, int prefix, boolean redirect) {
    if (!iface.matches("[a-zA-Z0-9_.-]{1,32}") || !Ipv4.valid(ip) || prefix < 1 || prefix > 32)
      throw new IllegalArgumentException("Invalid network interface");
    String match = " -i " + iface + " -s " + ip + "/" + prefix + " -d " + ip;
    return "set -e\n"
        + cleanup()
        + "iptables -w 3 -t nat -N "
        + CHAIN
        + "\n"
        + "iptables -w 3 -t nat -A "
        + CHAIN
        + match
        + " -p udp --dport 53 -j REDIRECT --to-ports 1053\n"
        + "iptables -w 3 -t nat -A "
        + CHAIN
        + match
        + " -p tcp --dport 53 -j REDIRECT --to-ports 1053\n"
        + (redirect
            ? "iptables -w 3 -t nat -A "
                + CHAIN
                + match
                + " -p tcp --dport 80 -j REDIRECT --to-ports 1080\n"
            : "")
        + "iptables -w 3 -t nat -I PREROUTING 1 -j "
        + CHAIN
        + "\n";
  }
}
