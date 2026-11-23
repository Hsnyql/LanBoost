package dev.lanboost.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class RootShell {
  private RootShell() {}

  public static String run(String script) throws IOException {
    Process p;
    try {
      p = new ProcessBuilder("su", "-c", script).redirectErrorStream(true).start();
    } catch (IOException e) {
      throw new IOException("无法访问 su，请在 KernelSU 中给 LanBoost 开启超级用户权限后重试", e);
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Thread reader =
        new Thread(
            () -> {
              try (InputStream stream = p.getInputStream()) {
                byte[] buf = new byte[2048];
                int count;
                while ((count = stream.read(buf)) != -1)
                  if (output.size() < 65536) output.write(buf, 0, count);
              } catch (IOException ignored) {
              }
            },
            "root-output");
    reader.start();
    try {
      if (!p.waitFor(25, TimeUnit.SECONDS)) {
        p.destroyForcibly();
        throw new IOException("Root 授权超时，请在 KernelSU 中允许 LanBoost");
      }
      reader.join(1000);
      String text = output.toString(StandardCharsets.UTF_8.name()).trim();
      if (p.exitValue() != 0) throw new IOException("Root 执行失败: " + text);
      return text;
    } catch (InterruptedException e) {
      p.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new IOException("Root 操作已取消", e);
    }
  }
}
