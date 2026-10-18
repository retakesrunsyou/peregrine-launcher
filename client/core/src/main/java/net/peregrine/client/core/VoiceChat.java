package net.peregrine.client.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.peregrine.client.core.modules.Cosmetics;

/**
 * Voice parties: talk with up to 7 friends from inside the game, on any server. Start a party
 * to get a 6-letter code, friends join with the code. Your voice goes through the Peregrine
 * service's party room (nothing is stored); you prove who you are with your Minecraft key.
 */
public final class VoiceChat {

    public static final int RATE = 16000, FRAME = 320;        // 20 ms frames at 16 kHz
    static final int FRAMES_PER_PACKET = 3;                     // 60 ms in each message
    static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);

    /** Someone in the party. */
    public static final class Member {
        public final int id;
        public final String uuid, name;
        public final boolean muted;
        volatile long heardAt;

        Member(int id, String uuid, String name, boolean muted) {
            this.id = id;
            this.uuid = uuid;
            this.name = name;
            this.muted = muted;
        }

        public boolean talking() {
            return System.currentTimeMillis() - heardAt < 250;
        }
    }

    // settings, set from the Voice chat module
    public static volatile boolean pushToTalk;
    public static volatile boolean pttHeld;          // the push-to-talk key, read on the game's thread
    public static volatile boolean micMuted;
    public static volatile float sensitivity = 0.35f; // voice activation: 0 = everything, 1 = only loud
    public static volatile float volume = 1f;
    public static volatile float micLevel;            // 0..1, for the meter

    private static volatile String party;            // the code, or null
    private static volatile String status = "";
    private static volatile boolean connecting;
    private static volatile int myId;
    private static volatile long talkingUntil;
    private static volatile List<Member> members = Collections.emptyList();
    private static volatile WebSocket socket;
    private static volatile boolean running;
    private static CompletableFuture<?> sending = CompletableFuture.completedFuture(null);
    private static final Map<Integer, ConcurrentLinkedQueue<short[]>> incoming = new ConcurrentHashMap<Integer, ConcurrentLinkedQueue<short[]>>();
    private static final Map<Integer, Boolean> started = new ConcurrentHashMap<Integer, Boolean>();

    /** For tests: stands in for the Minecraft key proof. */
    static volatile java.util.function.Function<String, JsonObject> proofForTests;

    private VoiceChat() {
    }

    /** For previews of the menu: a party with a few people in it. */
    static void previewParty() {
        party = "K2SLVM";
        myId = 1;
        previewing = true;
        List<Member> l = new ArrayList<Member>();
        l.add(new Member(1, "", "Kestrel", false));
        Member b = new Member(2, "", "Merlin", false);
        b.heardAt = Long.MAX_VALUE / 2;
        l.add(b);
        l.add(new Member(3, "", "Harrier", true));
        members = l;
    }

    /** For tests: sends one packet of voice as if from the microphone. */
    static void sendForTests(byte[] packet) {
        send(ByteBuffer.wrap(packet));
    }

    public static String party() {
        return party;
    }

    public static String status() {
        return status;
    }

    public static boolean connecting() {
        return connecting;
    }

    private static volatile boolean previewing;

    public static boolean inParty() {
        return party != null && myId > 0 && (socket != null || previewing);
    }

    public static List<Member> members() {
        return members;
    }

    public static int myId() {
        return myId;
    }

    /** Am I talking right now (sending my voice)? */
    public static boolean talking() {
        return System.currentTimeMillis() < talkingUntil;
    }

    public static boolean available() {
        return Cosmetics.SERVICE != null && !Cosmetics.SERVICE.isEmpty();
    }

    /** A new party code: easy to read out (no 0/O or 1/I). */
    public static String newCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            b.append(chars.charAt(r.nextInt(chars.length())));
        }
        return b.toString();
    }

    /** Joins (or starts) the party with this code. */
    public static void join(final String code, final Platform p) {
        final String c = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (!c.matches("[A-Z0-9]{6}")) {
            status = "A party code is 6 letters and numbers";
            return;
        }
        if (!available()) {
            status = "Voice needs the Peregrine service (start the game from the launcher)";
            return;
        }
        final String token = p.accessToken();
        final String uuid = p.playerUuid() == null ? "" : p.playerUuid().replace("-", "").toLowerCase();
        final String name = p.playerName();
        if (token == null || token.length() < 20 || uuid.length() != 32) {
            status = "Sign in with a Microsoft account to use voice";
            return;
        }
        leave();
        connecting = true;
        status = "Joining " + c + "...";
        Thread t = new Thread(() -> {
            try {
                String base = Cosmetics.SERVICE.replaceAll("/+$", "");
                JsonObject hello = proofForTests != null ? proofForTests.apply(uuid) : Cosmetics.prove(base, token, uuid);
                hello.addProperty("type", "hello");
                hello.addProperty("name", name);
                String ws = base.replaceFirst("^http", "ws") + "/v1/voice/" + c;
                WebSocket s = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().newWebSocketBuilder()
                        .buildAsync(URI.create(ws), new Listener()).get();
                party = c;
                socket = s;
                send(hello.toString());
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                status = "Couldn't join: " + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
                party = null;
                socket = null;
            } finally {
                connecting = false;
            }
        }, "Peregrine voice join");
        t.setDaemon(true);
        t.start();
    }

    public static void leave() {
        running = false;
        WebSocket s = socket;
        socket = null;
        party = null;
        myId = 0;
        members = Collections.emptyList();
        incoming.clear();
        started.clear();
        if (s != null) {
            try {
                s.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception e) {
                s.abort();
            }
        }
    }

    public static void setMicMuted(boolean m) {
        micMuted = m;
        if (inParty()) {
            JsonObject o = new JsonObject();
            o.addProperty("type", "muted");
            o.addProperty("muted", m);
            send(o.toString());
        }
    }

    // ------------------------------------------------------------------ the connection

    private static synchronized void send(String text) {
        final WebSocket s = socket;
        if (s == null) {
            return;
        }
        sending = sending.handle((r, e) -> null).thenCompose(x -> s.sendText(text, true));
    }

    private static synchronized void send(ByteBuffer data) {
        final WebSocket s = socket;
        if (s == null) {
            return;
        }
        if (!sending.isDone()) {
            return;  // the line's busy: drop this bit of voice rather than fall behind
        }
        sending = s.sendBinary(data, true);
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder text = new StringBuilder();
        private ByteBuffer binary = ByteBuffer.allocate(8192);

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                handle(text.toString());
                text.setLength(0);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            if (binary.remaining() < data.remaining()) {
                binary = ByteBuffer.allocate(Math.max(binary.capacity() * 2, binary.position() + data.remaining()))
                        .put((ByteBuffer) binary.flip());
            }
            binary.put(data);
            if (last) {
                binary.flip();
                byte[] b = new byte[binary.remaining()];
                binary.get(b);
                binary.clear();
                heard(b);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            if (socket == ws) {
                String why = code == 4001 ? "The party couldn't check who you are" : code == 4002 ? "That party is full"
                        : code == 4003 ? "You joined from somewhere else" : "Disconnected from the party";
                leave();
                status = why;
            }
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            if (socket == ws) {
                leave();
                status = "Voice connection lost";
            }
        }
    }

    private static void handle(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String type = o.has("type") ? o.get("type").getAsString() : "";
            if (type.equals("welcome")) {
                myId = o.get("id").getAsInt();
                status = "";
                startAudio();
                if (micMuted) {
                    setMicMuted(true);
                }
            } else if (type.equals("members")) {
                List<Member> list = new ArrayList<Member>();
                Map<Integer, Long> heard = new ConcurrentHashMap<Integer, Long>();
                for (Member m : members) {
                    heard.put(m.id, m.heardAt);
                }
                JsonArray a = o.getAsJsonArray("members");
                for (JsonElement e : a) {
                    JsonObject m = e.getAsJsonObject();
                    Member mm = new Member(m.get("id").getAsInt(), m.get("uuid").getAsString(), m.get("name").getAsString(),
                            m.has("muted") && m.get("muted").getAsBoolean());
                    Long h = heard.get(mm.id);
                    mm.heardAt = h == null ? 0 : h;
                    list.add(mm);
                }
                list.sort((x, y) -> x.id - y.id);
                members = list;
            } else if (type.equals("denied")) {
                status = o.has("error") ? o.get("error").getAsString() : "Not allowed in";
            }
        } catch (Exception e) {
            // a message we don't understand: ignore it
        }
    }

    /** Voice from someone: [1][sender][seq][frames][frame...] */
    static void heard(byte[] b) {
        if (b.length < 4 || b[0] != 1) {
            return;
        }
        int sender = b[1] & 0xFF;
        int frames = b[3] & 0xFF;
        int size = Adpcm.size(FRAME);
        if (frames == 0 || 4 + frames * size > b.length) {
            return;
        }
        ConcurrentLinkedQueue<short[]> q = incoming.computeIfAbsent(sender, k -> new ConcurrentLinkedQueue<short[]>());
        for (int i = 0; i < frames; i++) {
            short[] pcm = new short[FRAME];
            Adpcm.decode(b, 4 + i * size, pcm, FRAME);
            q.add(pcm);
        }
        while (q.size() > 15) {  // more than 300 ms behind: catch up
            q.poll();
        }
        for (Member m : members) {
            if (m.id == sender) {
                m.heardAt = System.currentTimeMillis();
            }
        }
    }

    // ------------------------------------------------------------------ microphone and speakers

    private static void startAudio() {
        if (running) {
            return;
        }
        running = true;
        Thread mic = new Thread(VoiceChat::micLoop, "Peregrine voice mic");
        mic.setDaemon(true);
        mic.start();
        Thread out = new Thread(VoiceChat::speakerLoop, "Peregrine voice out");
        out.setDaemon(true);
        out.start();
    }

    private static void micLoop() {
        TargetDataLine line = null;
        try {
            line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, FORMAT));
            line.open(FORMAT, FRAME * 2 * 4);
            line.start();
        } catch (Exception e) {
            status = "No microphone found (you can still listen)";
            return;
        }
        byte[] raw = new byte[FRAME * 2];
        short[] pcm = new short[FRAME];
        int size = Adpcm.size(FRAME);
        // sent as [1][seq][frames][frame...]; the party room adds who it's from after the 1
        byte[] packet = new byte[3 + FRAMES_PER_PACKET * size];
        int inPacket = 0, seq = 0;
        long openUntil = 0;
        float noise = 0.01f;
        try {
            while (running) {
                int got = 0;
                while (got < raw.length && running) {
                    got += line.read(raw, got, raw.length - got);
                }
                double sum = 0;
                for (int i = 0; i < FRAME; i++) {
                    pcm[i] = (short) ((raw[i * 2] & 0xFF) | (raw[i * 2 + 1] << 8));
                    sum += (double) pcm[i] * pcm[i];
                }
                float rms = (float) Math.sqrt(sum / FRAME) / 32768f;
                micLevel = Math.min(1f, rms * 6f);
                // voice activation: louder than the room's background by the chosen margin
                noise = rms < noise ? noise * 0.9f + rms * 0.1f : noise * 0.999f + rms * 0.001f;
                float threshold = Math.max(0.004f, noise * (1.5f + sensitivity * 6f));
                long now = System.currentTimeMillis();
                boolean open = pushToTalk ? pttHeld : rms > threshold;
                if (open) {
                    openUntil = now + 300;  // keep going a moment after you stop
                }
                boolean sendIt = !micMuted && (pushToTalk ? pttHeld : now < openUntil) && inParty();
                if (!sendIt) {
                    inPacket = 0;
                    continue;
                }
                talkingUntil = now + 120;
                Adpcm.encode(pcm, FRAME, packet, 3 + inPacket * size);
                inPacket++;
                if (inPacket == FRAMES_PER_PACKET) {
                    packet[0] = 1;
                    packet[1] = (byte) seq++;
                    packet[2] = (byte) inPacket;
                    send(ByteBuffer.wrap(packet.clone()));
                    inPacket = 0;
                }
            }
        } finally {
            line.stop();
            line.close();
            micLevel = 0;
        }
    }

    private static void speakerLoop() {
        SourceDataLine line;
        try {
            line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, FORMAT));
            line.open(FORMAT, FRAME * 2 * 6);
            line.start();
        } catch (Exception e) {
            status = "No speakers found for voice";
            return;
        }
        byte[] out = new byte[FRAME * 2];
        int[] mix = new int[FRAME];
        try {
            while (running) {
                java.util.Arrays.fill(mix, 0);
                for (Map.Entry<Integer, ConcurrentLinkedQueue<short[]>> e : incoming.entrySet()) {
                    ConcurrentLinkedQueue<short[]> q = e.getValue();
                    // a little cushion before someone starts, so their voice doesn't stutter
                    if (!started.containsKey(e.getKey())) {
                        if (q.size() < 3) {
                            continue;
                        }
                        started.put(e.getKey(), Boolean.TRUE);
                    }
                    short[] f = q.poll();
                    if (f == null) {
                        started.remove(e.getKey());
                        continue;
                    }
                    for (int i = 0; i < FRAME; i++) {
                        mix[i] += f[i];
                    }
                }
                float v = volume;
                for (int i = 0; i < FRAME; i++) {
                    int s = Math.max(-32768, Math.min(32767, Math.round(mix[i] * v)));
                    out[i * 2] = (byte) s;
                    out[i * 2 + 1] = (byte) (s >> 8);
                }
                line.write(out, 0, out.length);  // waits for room: keeps the 20 ms pace
            }
        } finally {
            line.drain();
            line.stop();
            line.close();
        }
    }
}
