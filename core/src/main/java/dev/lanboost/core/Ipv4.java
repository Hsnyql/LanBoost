package dev.lanboost.core;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class Ipv4 {
  private Ipv4() {}

  public static boolean valid(String value) {
    if (value == null) return false;
    String[] parts = value.split("\\.", -1);
    if (parts.length != 4) return false;
    for (String p : parts) {
      if (!p.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(p) > 255) return false;
    }
    return true;
  }

  public static InetAddress address(String value) {
    if (!valid(value)) throw new IllegalArgumentException("Invalid IPv4: " + value);
    String[] parts = value.split("\\.");
    byte[] bytes = new byte[4];
    for (int i = 0; i < 4; i++) bytes[i] = (byte) Integer.parseInt(parts[i]);
    try {
      return InetAddress.getByAddress(bytes);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static boolean sameSubnet(String a, String b, int prefix) {
    if (prefix < 1 || prefix > 32) return false;
    byte[] aa = address(a).getAddress(), bb = address(b).getAddress();
    for (int i = 0; i < prefix; i++)
      if ((aa[i / 8] & (128 >> (i % 8))) != (bb[i / 8] & (128 >> (i % 8)))) return false;
    return true;
  }
}
