package com.coolfreeze23.headinajar.client.fx;

/** One meaning, one colour (house palette D, art/palette.json). Dark step for sheaths, pale step for cores. */
public final class FxColors {
    /** Signal: the mind reaching out (Telekinesis, the swap). Teal glass. */
    public static final int SIGNAL_DARK = 0x39787e, SIGNAL = 0x86cac1, SIGNAL_PALE = 0xbeede1;
    /** Wet: saline. */
    public static final int WET_DARK = 0x4ea9ab, WET = 0x7bcdc6, WET_PALE = 0xb4ede2;
    /** Power: FE moving. Indicator amber. */
    public static final int POWER_DARK = 0xd47c16, POWER = 0xffc24e, POWER_PALE = 0xfff2c4;
    /** Weapon heat: brass pale (D). */
    public static final int HEAT = 0xf2c650, HEAT_PALE = 0xfff1a4;
    /** Scan: screen phosphor. */
    public static final int SCAN = 0x2f8a4e, SCAN_PALE = 0x8cf0a8;
    /** Warning: indicator red, always steady. */
    public static final int WARN = 0xff5a36;
    /** Refused, inert: dark steel (D). */
    public static final int GREY_DARK = 0x5c616b, GREY = 0xa1a7af;

    private FxColors() {
    }
}
