package dev.skylyrics.core;

/** Opacity and local scale only; there is no position/translation animation. */
public final class AnimationController {
    private AnimationController() { }

    public static float alpha(WorldPhrase phrase, long nowNs) {
        if (nowNs < phrase.bornNs() || nowNs >= phrase.expiresNs()) return 0;
        double duration = phrase.expiresNs() - phrase.bornNs();
        double fadeIn = Math.min(phrase.fadeInNs(), duration / 2);
        double fadeOut = Math.min(phrase.fadeOutNs(), duration / 2);
        double enter = fadeIn == 0 ? 1 : (nowNs - phrase.bornNs()) / fadeIn;
        double leave = fadeOut == 0 ? 1 : (phrase.expiresNs() - nowNs) / fadeOut;
        double t = Math.max(0, Math.min(1, Math.min(enter, leave)));
        return smooth(t);
    }

    public static float alpha(WorldPhrase phrase, long musicalNs, long realNs) {
        if (!phrase.retiring()) return alpha(phrase, musicalNs);
        if (phrase.retirementDurationNs() == 0) return 0;
        double progress = (realNs - phrase.retiredAtNs()) / (double) phrase.retirementDurationNs();
        return phrase.retirementAlpha() * (1 - smooth(progress));
    }

    public static float scale(WorldPhrase phrase, long musicalNs) {
        if (phrase.retiring()) return phrase.retirementScale();
        double fade = Math.min(phrase.fadeInNs(), (phrase.expiresNs() - phrase.bornNs()) / 2.0);
        return 0.95f + 0.05f * smooth(fade == 0 ? 1 : (musicalNs - phrase.bornNs()) / fade);
    }

    private static float smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return (float) (t * t * (3 - 2 * t));
    }
}
