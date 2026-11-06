package dev.skylyrics.core;

/** Opacity only: animation can never change the world anchor. Uses a monotonic clock. */
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
        return (float) (t * t * (3 - 2 * t));
    }
}
