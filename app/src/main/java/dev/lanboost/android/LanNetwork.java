package dev.lanboost.android;

import android.content.Context;
import android.net.*;
import dev.lanboost.core.*;
import java.io.IOException;
import java.net.*;

public final class LanNetwork {
  public final Network network;
  public final String ip, iface;
  public final int prefix;

  private LanNetwork(Network network, String ip, String iface, int prefix) {
    this.network = network;
    this.ip = ip;
    this.iface = iface;
    this.prefix = prefix;
  }

  public static LanNetwork find(Context c) throws IOException {
    ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    for (Network n : cm.getAllNetworks()) {
      NetworkCapabilities caps = cm.getNetworkCapabilities(n);
      LinkProperties props = cm.getLinkProperties(n);
      if (caps == null
          || props == null
          || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
          || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
      for (LinkAddress a : props.getLinkAddresses())
        if (a.getAddress() instanceof Inet4Address && !a.getAddress().isLoopbackAddress())
          return new LanNetwork(
              n, a.getAddress().getHostAddress(), props.getInterfaceName(), a.getPrefixLength());
    }
    throw new IOException("未连接带 IPv4 地址的 Wi-Fi");
  }

  public boolean same(LanNetwork other) {
    return network.equals(other.network)
        && ip.equals(other.ip)
        && iface.equals(other.iface)
        && prefix == other.prefix;
  }

  public boolean allows(InetAddress address) {
    return address instanceof Inet4Address && Ipv4.sameSubnet(ip, address.getHostAddress(), prefix);
  }

  public DnsUpstream.Binder binder() {
    return new DnsUpstream.Binder() {
      public void bind(DatagramSocket s) throws IOException {
        network.bindSocket(s);
      }

      public void bind(Socket s) throws IOException {
        network.bindSocket(s);
      }
    };
  }
}
