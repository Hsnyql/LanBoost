package dev.lanboost.core;

import java.io.IOException;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public final class DnsEngine {
  public interface Upstream {
    Message resolve(Message query) throws IOException;
  }

  private final Rules rules;
  private final Upstream upstream;

  public DnsEngine(Rules rules, Upstream upstream) {
    this.rules = rules;
    this.upstream = upstream;
  }

  public byte[] answer(byte[] wire, boolean tcp) throws IOException {
    Message q = new Message(wire);
    if (q.getHeader().getFlag(Flags.QR)) throw new IOException("Response received as query");
    Record question = q.getQuestion();
    Message out;
    if (q.getHeader().getOpcode() != Opcode.QUERY
        || q.getHeader().getCount(Section.QUESTION) != 1
        || question == null) {
      out = reply(q, Rcode.FORMERR);
    } else if (question.getDClass() != DClass.IN
        || question.getType() == Type.AXFR
        || question.getType() == Type.IXFR) {
      out = reply(q, Rcode.REFUSED);
    } else {
      String host = question.getName().toString();
      String target = rules.ipFor(host);
      if (target != null && question.getType() == Type.A) {
        out = reply(q, Rcode.NOERROR);
        out.addRecord(
            new ARecord(question.getName(), DClass.IN, 60, Ipv4.address(target)), Section.ANSWER);
      } else if (rules.suppressIpv6(host)
          && (question.getType() == Type.AAAA
              || question.getType() == Type.HTTPS
              || question.getType() == Type.SVCB)) {
        out = reply(q, Rcode.NOERROR);
      } else {
        try {
          out = upstream.resolve(q);
          if (!validResponse(q, out)) throw new IOException("Mismatched DNS response");
          rewriteAdditionalAddresses(out);
        } catch (IOException e) {
          out = reply(q, Rcode.SERVFAIL);
        }
      }
    }
    int limit =
        tcp
            ? 65535
            : q.getOPT() == null ? 512 : Math.max(512, Math.min(1232, q.getOPT().getPayloadSize()));
    return out.toWire(limit);
  }

  public static boolean validResponse(Message q, Message r) {
    return r.getHeader().getFlag(Flags.QR)
        && r.getHeader().getID() == q.getHeader().getID()
        && r.getQuestion() != null
        && r.getQuestion().equals(q.getQuestion());
  }

  private void rewriteAdditionalAddresses(Message response) {
    // SRV answers may carry target addresses, so clients need not issue another A query.
    boolean changed = false;
    for (int section : new int[] {Section.ANSWER, Section.ADDITIONAL}) {
      for (Record record : new java.util.ArrayList<>(response.getSection(section))) {
        String name = record.getName().toString(), ip = rules.ipFor(name);
        if (ip != null && record.getType() == Type.A) {
          response.removeRecord(record, section);
          response.addRecord(
              new ARecord(
                  record.getName(), DClass.IN, Math.min(60, record.getTTL()), Ipv4.address(ip)),
              section);
          changed = true;
        } else if (record.getType() == Type.AAAA && rules.suppressIpv6(name)) {
          response.removeRecord(record, section);
          changed = true;
        }
      }
    }
    if (changed) response.getHeader().unsetFlag(Flags.AD);
  }

  static Message reply(Message q, int code) {
    Message out = new Message(q.getHeader().getID());
    out.getHeader().setFlag(Flags.QR);
    out.getHeader().setFlag(Flags.RA);
    if (q.getHeader().getFlag(Flags.RD)) out.getHeader().setFlag(Flags.RD);
    out.getHeader().setRcode(code);
    if (q.getQuestion() != null) out.addRecord(q.getQuestion(), Section.QUESTION);
    return out;
  }
}
