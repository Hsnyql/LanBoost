package dev.lanboost.core;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.xbill.DNS.Message;
import org.xbill.DNS.Rcode;

public final class FailoverUpstream implements DnsEngine.Upstream {
  private final List<DnsEngine.Upstream> resolvers;

  public FailoverUpstream(DnsEngine.Upstream... resolvers) {
    this.resolvers = Arrays.asList(resolvers);
  }

  @Override
  public Message resolve(Message query) throws IOException {
    IOException last = new IOException("没有可用的上游 DNS");
    for (DnsEngine.Upstream resolver : resolvers)
      try {
        Message answer = resolver.resolve(query);
        if (!DnsEngine.validResponse(query, answer)) throw new IOException("上游 DNS 应答不匹配");
        if (answer.getRcode() == Rcode.SERVFAIL || answer.getRcode() == Rcode.REFUSED)
          throw new IOException("上游 DNS 暂不可用");
        return answer;
      } catch (IOException e) {
        last = e;
      }
    throw last;
  }
}
