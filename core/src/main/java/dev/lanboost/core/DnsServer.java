package dev.lanboost.core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.xbill.DNS.*;

public final class DnsServer implements AutoCloseable {
  private final DnsEngine engine;
  private final Predicate<InetAddress> allow;
  private final Consumer<String> log;
  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64));
  private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
  private DatagramSocket udp;
  private ServerSocket tcp;
  private volatile boolean running;

  public DnsServer(DnsEngine engine, Predicate<InetAddress> allow, Consumer<String> log) {
    this.engine = engine;
    this.allow = allow;
    this.log = log;
  }

  public synchronized void start(InetAddress address, int port) throws IOException {
    try {
      udp = new DatagramSocket(null);
      udp.setReuseAddress(false);
      udp.bind(new InetSocketAddress(address, port));
      tcp = new ServerSocket();
      tcp.setReuseAddress(false);
      tcp.bind(new InetSocketAddress(address, udp.getLocalPort()), 16);
      running = true;
      new Thread(this::receiveUdp, "dns-udp").start();
      new Thread(this::receiveTcp, "dns-tcp").start();
    } catch (IOException e) {
      close();
      throw e;
    }
  }

  public int port() {
    return udp.getLocalPort();
  }

  private void receiveUdp() {
    while (running) {
      try {
        byte[] buf = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        udp.receive(packet);
        if (!allow.test(packet.getAddress())) continue;
        byte[] query = Arrays.copyOf(buf, packet.getLength());
        workers.execute(
            () -> {
              try {
                byte[] response = engine.answer(query, false);
                udp.send(new DatagramPacket(response, response.length, packet.getSocketAddress()));
                record(query, response, packet.getAddress());
              } catch (IOException | IllegalArgumentException ignored) {
              }
            });
      } catch (RejectedExecutionException ignored) {
      } catch (IOException e) {
        if (running) log.accept("DNS UDP: " + e.getMessage());
      }
    }
  }

  private void receiveTcp() {
    while (running) {
      try {
        Socket socket = tcp.accept();
        socket.setSoTimeout(3000);
        if (!allow.test(socket.getInetAddress())) {
          socket.close();
          continue;
        }
        clients.add(socket);
        try {
          workers.execute(() -> serveTcp(socket));
        } catch (RejectedExecutionException e) {
          clients.remove(socket);
          socket.close();
        }
      } catch (IOException e) {
        if (running) log.accept("DNS TCP: " + e.getMessage());
      }
    }
  }

  private void serveTcp(Socket socket) {
    try (Socket ignored = socket) {
      DataInputStream in = new DataInputStream(socket.getInputStream());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      for (int i = 0; i < 64 && running; i++) {
        int size = in.readUnsignedShort();
        if (size < 12 || size > 4096) break;
        byte[] query = new byte[size];
        in.readFully(query);
        byte[] response = engine.answer(query, true);
        out.writeShort(response.length);
        out.write(response);
        out.flush();
        record(query, response, socket.getInetAddress());
      }
    } catch (IOException | IllegalArgumentException ignored) {
    } finally {
      clients.remove(socket);
    }
  }

  private void record(byte[] query, byte[] response, InetAddress source) {
    try {
      Message q = new Message(query), r = new Message(response);
      if (q.getQuestion() != null)
        log.accept(
            source.getHostAddress()
                + " "
                + Type.string(q.getQuestion().getType())
                + " "
                + q.getQuestion().getName()
                + " "
                + Rcode.string(r.getRcode()));
    } catch (IOException ignored) {
    }
  }

  @Override
  public synchronized void close() {
    running = false;
    if (udp != null) udp.close();
    if (tcp != null)
      try {
        tcp.close();
      } catch (IOException ignored) {
      }
    for (Socket s : clients)
      try {
        s.close();
      } catch (IOException ignored) {
      }
    workers.shutdownNow();
  }
}
