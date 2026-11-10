package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModSounds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * DOLORES's voice. Each line has one sound event per language (and a muffled one for the lines the patient hears
 * during the operation). The server only says which line, from which chair, and for which language's timing; each
 * client plays the language it is set to. Lengths, the muffled set, the operation's schedule and the ducking list are
 * read from {@code assets/headinajar/dolores_voice.json}, which tools/gen_p13_chair.py writes from the voice hand-off.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DoloresVoice {
    public static final String EN = "en", PT = "pt", BOTH = "";

    /** A line: whether the patient hears a muffled copy, and how long it is heard, in ticks, per language. */
    public record Line(String id, boolean muffled, int enTicks, int ptTicks) {
        public int ticks(String lang) {
            return PT.equals(lang) ? ptTicks : enTicks;
        }
    }

    public static final Map<String, Line> LINES = new LinkedHashMap<>();
    /** Operation tick -> line, per language. */
    private static final Map<String, Map<Integer, String>> OPERATION = new HashMap<>();
    private static final Map<String, Integer> DONE_DELAY = new HashMap<>(), OOPSIE_DELAY = new HashMap<>();
    /** Chair sounds that play quieter under her voice, and by how much. */
    public static final Set<String> DUCK = new HashSet<>();
    public static float duckDb = -6;
    /** At most one refusal line from a chair in this many ticks; the same refusal at most once per sitting. */
    public static final int REFUSAL_GAP = 60;

    static {
        try (var in = DoloresVoice.class.getResourceAsStream("/assets/headinajar/dolores_voice.json")) {
            if (in != null) {
                JsonObject table = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : table.getAsJsonObject("lines").entrySet()) {
                    JsonObject l = e.getValue().getAsJsonObject();
                    LINES.put(e.getKey(), new Line(e.getKey(), l.get("muffled").getAsBoolean(), l.get("en").getAsInt(), l.get("pt").getAsInt()));
                }
                for (Map.Entry<String, JsonElement> e : table.getAsJsonObject("schedule").entrySet()) {
                    JsonObject lang = e.getValue().getAsJsonObject();
                    Map<Integer, String> ops = new TreeMap<>();
                    for (Map.Entry<String, JsonElement> op : lang.getAsJsonObject("operation").entrySet()) {
                        ops.put(Integer.parseInt(op.getKey()), op.getValue().getAsString());
                    }
                    OPERATION.put(e.getKey(), ops);
                    DONE_DELAY.put(e.getKey(), lang.get("done_delay").getAsInt());
                    OOPSIE_DELAY.put(e.getKey(), lang.get("oopsie_delay").getAsInt());
                }
                table.getAsJsonArray("duck").forEach(d -> DUCK.add(d.getAsString()));
                duckDb = table.get("duck_db").getAsFloat();
            }
        } catch (Exception e) {
            HeadInAJar.LOGGER.warn("DOLORES's voice table could not be read; she will only speak in text", e);
        }
    }

    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();

    static {
        for (Line line : LINES.values()) {
            for (String lang : new String[]{EN, PT}) {
                register("dolores." + lang + "." + line.id());
                if (line.muffled()) register("dolores." + lang + "." + line.id() + ".muffled");
            }
        }
    }

    private static void register(String name) {
        ModSounds.SOUNDS.register(name, () -> {
            SoundEvent event = SoundEvent.createVariableRangeEvent(HeadInAJar.id(name));
            EVENTS.put(name, event);
            return event;
        });
    }

    /** Loads the class, so the events are registered with the mod's sound register. */
    public static void load() {
    }

    /** The registered event for a line in a language, clear or muffled, or null. */
    public static SoundEvent event(String line, String lang, boolean muffled) {
        return EVENTS.get("dolores." + lang + "." + line + (muffled ? ".muffled" : ""));
    }

    /** The line each language starts at this operation tick, if any. */
    public static String operationLine(String lang, int tick) {
        Map<Integer, String> ops = OPERATION.get(lang);
        return ops == null ? null : ops.get(tick);
    }

    /** The operation's schedule for a language (tick -> line). */
    public static Map<Integer, String> operation(String lang) {
        return OPERATION.getOrDefault(lang, Map.of());
    }

    /** Ticks after the end of the operation before the closing line (or "Oopsie.") starts, per language. */
    public static int afterDelay(String lang, boolean botched) {
        return (botched ? OOPSIE_DELAY : DONE_DELAY).getOrDefault(lang, 24);
    }

    /** The voice line for a DOLORES or refusal text key, or null when that text isn't voiced. */
    public static String lineFor(String textKey) {
        String id = textKey.replace("dolores.headinajar.", "").replace("refusal.headinajar.", "").replace('.', '_');
        return LINES.containsKey(id) ? id : null;
    }

    /** Server to client: the chair at {@code pos} says {@code line}; {@code lang} empty = every language, else that one. */
    public record Say(BlockPos pos, String line, String lang) implements CustomPacketPayload {
        public static final Type<Say> TYPE = new Type<>(HeadInAJar.id("dolores_say"));
        public static final StreamCodec<ByteBuf, Say> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Say::pos, ByteBufCodecs.stringUtf8(32), Say::line, ByteBufCodecs.stringUtf8(4), Say::lang, Say::new);

        @Override
        public Type<Say> type() {
            return TYPE;
        }
    }

    /** The last lines sent (game time, chair, line, language), for tests and commands. */
    public static final ArrayDeque<String> HISTORY = new ArrayDeque<>();

    /** Sends a line to every player tracking the chair (the patient included). */
    public static void say(ServerLevel level, BlockPos pos, String line, String lang) {
        if (!LINES.containsKey(line)) return;
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), new Say(pos, line, lang));
        HISTORY.add(level.getGameTime() + " " + pos.toShortString() + " " + line + (lang.isEmpty() ? "" : " " + lang));
        if (HISTORY.size() > 64) HISTORY.removeFirst();
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Say.TYPE, Say.CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> com.coolfreeze23.headinajar.client.ChairVoiceClient.play(msg)));
    }

    private DoloresVoice() {
    }
}
