package io.github.miklires.mauth.risk;

public interface IpRiskProvider {

    String name();

    RiskResult check(String ip);

    record RiskResult(Status status, String detail) {
        public static RiskResult safe() { return new RiskResult(Status.SAFE, ""); }
        public static RiskResult risky(String detail) { return new RiskResult(Status.RISKY, detail); }
        public static RiskResult unavailable(String detail) { return new RiskResult(Status.UNAVAILABLE, detail); }
    }

    enum Status {
        SAFE, RISKY, UNAVAILABLE
    }
}
