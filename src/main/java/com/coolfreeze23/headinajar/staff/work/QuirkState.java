package com.coolfreeze23.headinajar.staff.work;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.UUID;

/**
 * One worker's quirk timers and what a quirk is in the middle of (not saved: a reload starts every timer again and
 * stands a sitting cat up, as vanilla does). Kept by {@link Quirks}, one for each worker whose quirks think.
 */
public final class QuirkState {
    /** Why a quirk holds the worker up (a stop line's id), or null; it holds until {@link #holdUntil}. */
    public String holdKey;
    public long holdUntil;

    // the cat on a chest
    /** The chest it is going to sit on, or sitting on; null: none. */
    public BlockPos chest;
    public boolean sitting;
    /** When it gets up again (sitting), or gives up getting there (on its way). */
    public long sitUntil;
    /** Thinks spent beside the chest without being on it (it hops up after a few). */
    public int hops;
    public long nextSit;

    // cats and ocelots keep creepers out
    public long nextSweep;

    // the piglin's pocket: items taken since the last skim
    public int skimTally;

    // the llama: the day it last spat at whoever opened its clock (-1 none)
    public long spatDay = -1;

    // the panda
    /** A lazy panda is on its break (or will take it once its hands are free). */
    public boolean lazing, lazeDue;
    public long nextLaze, nextRoll, nextGuard;

    // the goat: who it is about to ram
    public UUID ramTarget;
    public long nextRam;

    // the dolphin
    public boolean surfacing;

    // the villager: the day its trades were last restocked (-1: not seen yet)
    public long restockDay = -1;

    // the iron golem and the witch
    public long nextPoppy, nextSplash;

    QuirkState(long now, RandomSource random) {
        nextSit = now + 200 + random.nextInt(600);
        nextSweep = now;
        nextLaze = now + Quirks.LAZY_TICKS;
        nextRoll = now + 100 + random.nextInt(300);
        nextGuard = now;
        nextRam = now + 600 + random.nextInt(2400);
        nextPoppy = now + 200 + random.nextInt(400);
        nextSplash = now + Quirks.SPLASH_TICKS;
    }

    /** Every timer due now (a test, or an operator's nudge): the next think does what each one waits for. */
    public void hurry() {
        nextSit = nextSweep = nextLaze = nextRoll = nextGuard = nextRam = nextPoppy = nextSplash = 0;
        if (sitting) sitUntil = 0;
    }

    void hold(String key, long now, int ticks) {
        holdKey = key;
        holdUntil = key == null ? 0 : now + ticks;
    }
}
