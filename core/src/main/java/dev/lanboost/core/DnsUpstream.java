package dev.lanboost.core;

import java.io.*;
import java.net.*;
import org.xbill.DNS.*;

public final class DnsUpstream implements DnsEngine.Upstream {
  public interface Binder {
    void bind(DatagramSocket socket) throws IOException;

    void bind(Socket socket) throws IOException;
  }

  public static final Binder DEFAULT =
      new Binder() {
        public void bind(DatagramSocket socket) {}

        public void bind(Socket socket) {}
      };
  private final InetAddress host;
  private final int port;
  private final Binder binder;

  public DnsUpstream(String ip, int port, Binder binder) {
    host = Ipv4.address(ip);
    this.port = port;
    this.binder = binder;
  }

  public Message resolve(Message query) throws IOException {
    byte[] request = query.toWire();
    try (DatagramSocket socket = new DatagramSocket(null)) {
      binder.bind(socket);
      socket.bind(new InetSocketAddress(0));
      socket.connect(host, port);
      socket.setSoTimeout(2500);
      socket.send(new DatagramPacket(request, request.length));
      byte[] buf = new byte[65535];
      DatagramPacket packet = new DatagramPacket(buf, buf.length);
      socket.receive(packet);
      Message result = new Message(java.util.Arrays.copyOf(buf, packet.getLength()));
      if (!DnsEngine.validResponse(query, result))
        throw new IOException("Mismatched upstream response");
      if (!result.getHeader().getFlag(Flags.TC)) return result;
    }
    try (Socket socket = new Socket()) {
      binder.bind(socket);
      socket.connect(new InetSocketAddress(host, port), 2500);
      socket.setSoTimeout(2500);
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      out.writeShort(request.length);
      out.write(request);
      out.flush();
      DataInputStream in = new DataInputStream(socket.getInputStream());
      byte[] buf = new byte[in.readUnsignedShort()];
      in.readFully(buf);
      Message result = new Message(buf);
      if (!DnsEngine.validResponse(query, result)) throw new IOException("Mismatched TCP response");
      return result;
    }
  }
}
