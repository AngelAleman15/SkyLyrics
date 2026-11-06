package dev.skylyrics.core;

/** One appearance. Only its musical deadline can change; identity and position are final. */
public final class WorldPhrase {
    private final long id, bornNs, fadeInNs, fadeOutNs;
    private final String text;
    private final WorldAnchor anchor;
    private long expiresNs;
    private long retiredAtNs = Long.MIN_VALUE, retirementDurationNs;
    private float retirementAlpha, retirementScale = 1;

    public WorldPhrase(long id, String text, WorldAnchor anchor,
                       long bornNs, long expiresNs, long fadeInNs, long fadeOutNs) {
        if (text == null || anchor == null || expiresNs <= bornNs
                || fadeInNs < 0 || fadeOutNs < 0) {
            throw new IllegalArgumentException("Invalid phrase");
        }
        this.id = id;
        this.text = text;
        this.anchor = anchor;
        this.bornNs = bornNs;
        this.expiresNs = expiresNs;
        this.fadeInNs = fadeInNs;
        this.fadeOutNs = fadeOutNs;
    }

    public long id() { return id; }
    public String text() { return text; }
    public WorldAnchor anchor() { return anchor; }
    public long bornNs() { return bornNs; }
    public long expiresNs() { return expiresNs; }
    public long fadeInNs() { return fadeInNs; }
    public long fadeOutNs() { return fadeOutNs; }
    public void alignDeadline(long nowNs, long remainingMs) {
        if (retiring()) return;
        expiresNs = Math.max(bornNs + 1, nowNs + Math.max(0, remainingMs) * 1_000_000L);
    }
    public void finish(long nowNs) { if (!retiring()) expiresNs = Math.min(expiresNs, nowNs + fadeOutNs); }
    public boolean retiring() { return retiredAtNs != Long.MIN_VALUE; }
    public long retiredAtNs() { return retiredAtNs; }
    public long retirementDurationNs() { return retirementDurationNs; }
    public float retirementAlpha() { return retirementAlpha; }
    public float retirementScale() { return retirementScale; }

    public void retire(long musicalNs, long realNs, long durationMs) {
        if (retiring()) return;
        retirementAlpha = AnimationController.alpha(this, musicalNs);
        retirementScale = AnimationController.scale(this, musicalNs);
        retirementDurationNs = Math.max(0, durationMs) * 1_000_000L;
        retiredAtNs = realNs;
    }

    public boolean expired(long musicalNs, long realNs) {
        return retiring() ? realNs - retiredAtNs >= retirementDurationNs : musicalNs >= expiresNs;
    }
}
