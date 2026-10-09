import java.nio.file.*;
import java.util.*;
import net.peregrine.client.core.*;
import net.peregrine.client.core.Platform.*;

public class CoreTest {
    static double gamma = 0.5; static boolean sprint, zoomKey; static Path cfg;
    static Set<Key> down = new HashSet<>();
    static class P implements Platform {
        public boolean inWorld(){return true;} public int fps(){return 144;}
        public double x(){return 12.345;} public double y(){return 64;} public double z(){return -5.5;}
        public String facing(){return "North";} public boolean isDown(Key k){return down.contains(k);}
        public List<ItemInfo> armor(){return Arrays.asList(new ItemInfo("helmet",10,100), new ItemInfo("sword",900,1000));}
        public List<EffectInfo> effects(){return Arrays.asList(new EffectInfo("Speed",1,1500,false));}
        public void setSprintHeld(boolean h){sprint=h;} public boolean zoomKeyDown(){return zoomKey;}
        public double gamma(){return gamma;} public void setGamma(double v){gamma=v;}
        public int screenWidth(){return 480;} public int screenHeight(){return 270;}
        public Path configFile(){return cfg;}
        public void openScreen(Screen w){opened=w;} public boolean showing(Screen w){return playing && w==Screen.NONE;}
        public String playerName(){return "Benjamin";} public String minecraftVersion(){return "1.21.1";}
        public long worldTime(){return 6000;} public String biome(){return "minecraft:dark_forest";} public float yaw(){return 180f;}
        public int food(){return 18;} public float saturation(){return 5.2f;} public int potionCount(){return 4;}
        public java.util.List<String> resourcePacks(){return java.util.Arrays.asList("Faithful 32x","Default");}
        public void setOption(Option o, boolean on){ if(on) optionsOn.add(o); else optionsOn.remove(o); }
        public int ping(){return ping;} public String serverAddress(){return server;} public long worldDay(){return 42;}
        public float pitch(){return 12.5f;} public double targetDistance(){return target ? 2.87 : -1;}
        public String targetName(){return target ? "Zombie" : "";} public float targetHealth(){return target ? 14 : -1;}
        public String lookedAtBlock(){return "Oak Planks";} public int heldItemCount(){return 128;} public String heldItemName(){return "Cobblestone";}
        public int onlinePlayers(){return 23;} public int xpLevel(){return 30;} public float xpProgress(){return 0.25f;}
        public int lightLevel(){return 7;} public int hurtTime(){return hurt;}
        public String dimension(){return dim;}
        public void reloadChunks(){reloads++;} public boolean freelookKeyDown(){return altDown;}
        public int cameraMode(){return camera;} public void setCameraMode(int m){camera=m;}
        public void setHitColor(int c){hitColor=c;} public double guiScale(){return 2;}
    }
    static int reloads, camera, hitColor=-1; static boolean altDown;
    static boolean target; static int hurt; static String dim = "minecraft:overworld";
    static java.util.Set<Platform.Option> optionsOn=new java.util.HashSet<>();
    static boolean playing; static Platform.Screen opened; static int ping=-1; static String server=null;
    static List<String> texts = new ArrayList<>();
    static class D implements Draw {
        public void rect(int x,int y,int w,int h,int c){}
        public void text(String s,int x,int y,int c,boolean sh){texts.add(s+"@"+x+","+y);}
        public int width(String s){return s.length()*6;} public int lineHeight(){return 9;}
        public void item(Object st,int x,int y){} public void clip(int x,int y,int w,int h){} public void unclip(){}
        public void textScaled(String s,int x,int y,int c,float sc,boolean sh){texts.add(s+"@"+x+","+y);}
    }
    static int[] centerOfReflect(Menu m, String what) throws Exception {
        java.lang.reflect.Method c = Menu.class.getDeclaredMethod("centerOf", String.class); c.setAccessible(true);
        return (int[]) c.invoke(m, what);
    }
    static void check(boolean c, String m){ if(!c) throw new AssertionError(m); System.out.println("ok  " + m); }
    static int[] find(String prefix){ for(String t: texts) if(t.startsWith(prefix)){ String[] xy=t.substring(t.lastIndexOf('@')+1).split(","); return new int[]{Integer.parseInt(xy[0]),Integer.parseInt(xy[1])}; } throw new AssertionError("not drawn: "+prefix); }
    public static void main(String[] a) throws Exception {
        cfg = Files.createTempDirectory("pc").resolve("config/peregrine-client.json");
        Peregrine pc = Peregrine.init(new P()); D d = new D(); Menu m = pc.menu();
        check(pc.modules().size()==52, "52 modules registered");
        for (String id : new String[]{"health","nether_coords","session","totems","arrows","durability_alert",
                "reach","combo","target","block_info","block_count","players","rotation","stopwatch","xp","chunk","light",
                "anti_leak","crosshair","freelook","hit_color","item_physics"}) check(pc.module(id)!=null, id+" exists");
        // Defaults: only FPS, coordinates and armor (plus the title screen) start on, every HUD item at 50%
        for (net.peregrine.client.core.Module mod : pc.modules()) {
            boolean want = Arrays.asList("fps","coords","armor","main_menu").contains(mod.id);
            check(mod.enabled()==want, mod.id + (want ? " on" : " off") + " by default");
            if (mod instanceof HudModule) check(((HudModule)mod).scale==0.5f, mod.id + " starts at 50%");
        }
        texts.clear(); pc.renderHud(d);
        check(texts.stream().anyMatch(t->t.startsWith("Overworld@")), "coords show the dimension");
        dim = "minecraft:the_nether"; texts.clear(); pc.renderHud(d);
        check(texts.stream().anyMatch(t->t.startsWith("Nether@")), "...in the Nether too"); dim = "minecraft:overworld";
        pc.module("effects").setEnabled(true); texts.clear(); pc.renderHud(d);
        check(texts.stream().anyMatch(t->t.startsWith("144")), "FPS HUD shows 144");
        check(texts.stream().anyMatch(t->t.startsWith("12.3  64.0  -5.5")), "coords formatted");
        check(texts.stream().anyMatch(t->t.startsWith("Speed II")) && texts.stream().anyMatch(t->t.startsWith("1:15")), "effect name + time");
        check(texts.stream().anyMatch(t->t.startsWith("90@")), "armor durability left (100-10)");
        // menu: toggle CPS by clicking its card
        m.open(); Thread.sleep(200); texts.clear(); m.render(d,0,0);
        check(!pc.module("cps").enabled(), "CPS starts off");
        int[] cps = find("CPS@"); m.mouseClicked(cps[0]+2, cps[1]+2, 0);
        check(pc.module("cps").enabled(), "clicking the CPS card turns it on");
        // search
        for(char c: "toggle spr".toCharArray()) m.charTyped(c);
        texts.clear(); m.render(d,0,0);
        check(texts.stream().anyMatch(t->t.startsWith("Toggle sprint")) && texts.stream().noneMatch(t->t.startsWith("FPS@")), "search filters to Toggle sprint");
        m.keyPressed(Menu.KEY_ENTER);
        check(pc.module("toggle_sprint").enabled(), "Enter toggles the only match");
        pc.tick(); check(sprint, "toggle sprint holds sprint");
        pc.module("toggle_sprint").setEnabled(false); check(!sprint, "turning it off releases sprint");
        for(int i=0;i<10;i++) m.keyPressed(Menu.KEY_BACKSPACE);
        check(m.keyPressed(Menu.KEY_ESCAPE)==false, "Esc left for the screen to close the menu");
        // tabs
        texts.clear(); m.render(d,0,0); int[] util = find("Utility@"); m.mouseClicked(util[0]+1, util[1]+1, 0);
        texts.clear(); m.render(d,0,0);
        check(texts.stream().anyMatch(t->t.startsWith("Zoom@")) && texts.stream().noneMatch(t->t.startsWith("FPS@")), "Utility tab shows only utility");
        // zoom
        pc.module("zoom").setEnabled(true);
        for (net.peregrine.client.core.settings.Setting st : pc.module("zoom").settings()) if (st.id.equals("smooth")) ((net.peregrine.client.core.settings.BoolSetting)st).value=false;
        check(pc.zoomDivisor()==1.0, "no zoom without key"); zoomKey=true; check(pc.zoomDivisor()==4.0, "zoom key gives 4x"); zoomKey=false;
        // fullbright
        pc.module("fullbright").setEnabled(true); pc.tick(); check(gamma==16.0, "fullbright raises gamma");
        pc.module("fullbright").setEnabled(false); check(gamma==0.5, "and restores it");
        gamma=0.7; pc.shutdown(); check(gamma==0.7, "shutdown doesn't touch gamma when fullbright never ran");
        // HUD editor drag
        texts.clear(); m.render(d,0,0); int[] edit = find("Edit HUD@"); m.mouseClicked(edit[0], edit[1], 0);
        check(!m.wantsShade(), "edit mode turns off the dim background");
        pc.renderHud(d);  // positions get recorded
        HudModule fps = (HudModule) pc.module("fps");
        check(m.mouseClicked(2,2,0), "grab FPS box at top-left");
        m.mouseDragged(240,135); m.mouseReleased(); pc.renderHud(d);
        check(fps.fx>0.4 && fps.fx<0.6 && fps.fy>0.4 && fps.fy<0.6, "dragged to the middle: fx="+fps.fx+" fy="+fps.fy);
        // Lunar-style editing: right-click for style, pick a colour, scroll to resize
        texts.clear(); pc.renderHud(d); int[] fpsAt = centerOfReflect(m, "item:fps");
        m.mouseClicked(fpsAt[0], fpsAt[1], 1);
        texts.clear(); m.render(d, fpsAt[0], fpsAt[1]);
        check(texts.stream().anyMatch(t->t.startsWith("Size@")) && texts.stream().anyMatch(t->t.startsWith("Text shadow@")), "right-click opens the style panel");
        int[] textRow = find("Text@");
        m.mouseClicked(textRow[0] + 3*12 + 5, textRow[1] + 15, 0);
        check(fps.textColor == 0xFFFF5C5C, "picking a swatch colours the text: " + Integer.toHexString(fps.textColor));
        int[] bgRow = find("Background@"); m.mouseClicked(bgRow[0], bgRow[1], 0);
        check(!fps.background, "background can be switched off");
        m.mouseScrolled(1);
        check(Math.abs(fps.scale - 0.55f) < 1e-4, "scrolling over it makes it bigger: " + fps.scale);
        texts.clear(); pc.renderHud(d);
        check(texts.stream().anyMatch(t->t.startsWith("FPS@")), "styled item still draws");
        m.keyPressed(Menu.KEY_ESCAPE); check(m.wantsShade(), "Esc leaves the HUD editor");
        // CPS
        for(int i=0;i<7;i++) pc.onMouseButton(0); pc.onMouseButton(1);
        check(pc.cps(0)==7 && pc.cps(1)==1, "CPS counts clicks");
        // save + reload
        pc.shutdown(); String json = new String(Files.readAllBytes(cfg));
        check(json.contains("\"cps\"") && json.contains("\"enabled\": true"), "settings saved");
        Peregrine pc2 = Peregrine.init(new P());
        check(pc2.module("cps").enabled() && Math.abs(((HudModule)pc2.module("fps")).fx-fps.fx)<1e-6, "settings load back");
        HudModule fps2 = (HudModule) pc2.module("fps");
        check(fps2.textColor == 0xFFFF5C5C && !fps2.background && Math.abs(fps2.scale - 0.55f) < 1e-4, "HUD styles load back");
        // An old settings file (before version 2): switches go back to the new defaults, places stay
        Files.write(cfg, "{\"modules\":{\"keystrokes\":{\"enabled\":true,\"x\":0.3,\"y\":0.4},\"main_menu\":{\"enabled\":false}}}".getBytes());
        Peregrine old = Peregrine.init(new P());
        check(!old.module("keystrokes").enabled() && ((HudModule)old.module("keystrokes")).fx==0.3f, "old file: new defaults, positions kept");
        check(!old.module("main_menu").enabled(), "old file: main menu choice kept");
        Files.write(cfg, "{broken".getBytes()); Peregrine.init(new P()); check(true, "broken settings file doesn't crash");
        // ---- main menu
        Peregrine pc3 = Peregrine.init(new P()); TitleMenu t = pc3.titleMenu();
        check(t.enabled(), "main menu on by default");
        texts.clear(); t.render(d,0,0);
        check(texts.stream().anyMatch(x->x.startsWith("PEREGRINE@")) && texts.stream().anyMatch(x->x.startsWith("Welcome back, Benjamin@")), "big title + welcome line");
        check(texts.stream().anyMatch(x->x.startsWith("Peregrine Client " + TitleMenu.CLIENT_VERSION + " for Minecraft 1.21.1@")), "footer shows versions");
        String[] labels={"Singleplayer","Multiplayer","Options","Peregrine","Quit"};
        Platform.Screen[] want={Platform.Screen.SINGLEPLAYER,Platform.Screen.MULTIPLAYER,Platform.Screen.OPTIONS,Platform.Screen.PEREGRINE_MENU,Platform.Screen.QUIT};
        for(int i=0;i<labels.length;i++){ int[] xy=find(labels[i]+"@"); opened=null; t.mouseClicked(xy[0]+1, xy[1]+1, 0); check(opened==want[i], labels[i]+" button opens "+want[i]); }
        pc3.module("main_menu").setEnabled(false); check(!t.enabled(), "main menu can be switched off");
        // ---- new HUD items
        for(String id: new String[]{"clock","ping","server","speed","day"}) pc3.module(id).setEnabled(true);
        texts.clear(); pc3.renderHud(d);
        check(texts.stream().noneMatch(x->x.startsWith("Ping@")) && texts.stream().noneMatch(x->x.startsWith("Server@")), "ping/server hidden in singleplayer");
        check(texts.stream().anyMatch(x->x.startsWith("42   ")), "day counter");
        ping=37; server="mc.example.net"; texts.clear(); pc3.renderHud(d);
        check(texts.stream().anyMatch(x->x.startsWith("37 ms@")) && texts.stream().anyMatch(x->x.startsWith("mc.example.net@")), "ping + server shown on a server");
        // ---- batch 2
        Peregrine pc4 = Peregrine.init(new P());
        for(String id: new String[]{"compass","biome","memory","food","potion_count","packs","day"}) pc4.module(id).setEnabled(true);
        texts.clear(); pc4.renderHud(d);
        check(texts.stream().anyMatch(x->x.startsWith("Dark Forest@")), "biome name made readable");
        check(texts.stream().anyMatch(x->x.startsWith("N@")), "compass shows N when facing north (yaw 180)");
        check(texts.stream().anyMatch(x->x.startsWith("42   12:00@")), "world clock: noon on day 42");
        check(texts.stream().anyMatch(x->x.startsWith("18   Saturation 5.2@")), "food + saturation");
        check(texts.stream().anyMatch(x->x.startsWith("Faithful 32x@")), "resource packs listed");
        check(texts.stream().anyMatch(x->x.startsWith("4@")), "potion count");
        pc4.module("static_fov").setEnabled(true); pc4.tick();
        check(optionsOn.contains(Platform.Option.STATIC_FOV), "Static FOV switches Minecraft's setting");
        pc4.module("static_fov").setEnabled(false);
        check(!optionsOn.contains(Platform.Option.STATIC_FOV), "and puts it back when turned off");
        check(!pc4.on("clean_edges"), "clean edges off by default"); pc4.module("clean_edges").setEnabled(true); check(pc4.on("clean_edges"), "hook flag turns on");
        pc4.menu().open(); Thread.sleep(200); texts.clear(); pc4.menu().render(d,0,0); int[] vis=find("Visuals@"); pc4.menu().mouseClicked(vis[0]+1,vis[1]+1,0);
        texts.clear(); pc4.menu().render(d,0,0);
        check(texts.stream().anyMatch(x->x.startsWith("Steady camera@")) && texts.stream().noneMatch(x->x.startsWith("FPS@")), "Visuals tab");
        // ---- settings pages (gear on each row)
        Files.deleteIfExists(cfg);
        Peregrine pc5 = Peregrine.init(new P()); Menu m5 = pc5.menu();
        m5.open(); Thread.sleep(200); texts.clear(); m5.render(d,0,0);
        int[] gear = centerOfReflect(m5, "gear:coords");
        check(gear != null, "coordinates row has a gear");
        m5.mouseClicked(gear[0], gear[1], 0);
        texts.clear(); m5.render(d,0,0);
        check(texts.stream().anyMatch(q->q.startsWith("Customize look & position@")) && texts.stream().anyMatch(q->q.startsWith("Show dimension@")), "gear opens the settings page");
        check(pc5.module("coords").enabled(), "the gear doesn't switch it off");
        int[] dimRow = centerOfReflect(m5, "set:dimension"); m5.mouseClicked(dimRow[0], dimRow[1], 0);
        texts.clear(); pc5.renderHud(d);
        check(texts.stream().noneMatch(q->q.startsWith("Overworld@")), "turning off 'Show dimension' hides it");
        check(centerOfReflect(m5, "set:decimals") == null, "rows below the fold aren't clickable");
        m5.mouseScrolled(-3); Thread.sleep(120); m5.render(d,0,0); Thread.sleep(120); m5.render(d,0,0);
        int[] dec = centerOfReflect(m5, "set:decimals"); m5.mouseClicked(dec[0], dec[1], 0);
        texts.clear(); pc5.renderHud(d);
        check(texts.stream().anyMatch(q->q.startsWith("12.35  64.00  -5.50")), "decimals cycle to two");
        check(m5.keyPressed(Menu.KEY_ESCAPE), "Esc goes back from a settings page");
        texts.clear(); m5.render(d,0,0);
        check(texts.stream().anyMatch(q->q.startsWith("Edit HUD@")) && texts.stream().noneMatch(q->q.startsWith("Show dimension@")), "back on the list");
        // right-click a row: same page
        int[] ut = find("Utility@"); m5.mouseClicked(ut[0]+1, ut[1]+1, 0); texts.clear(); m5.render(d,0,0);
        int[] zoomRow = centerOfReflect(m5, "zoom"); m5.mouseClicked(zoomRow[0], zoomRow[1], 1);
        texts.clear(); m5.render(d,0,0);
        check(texts.stream().anyMatch(q->q.startsWith("Zoom level@")), "right-click opens settings too");
        int[] slider = centerOfReflect(m5, "set:level");
        m5.mouseClicked(slider[0], slider[1], 0); m5.mouseDragged(slider[0] + 400, slider[1]); m5.mouseReleased();
        float lvl = 0; for (net.peregrine.client.core.settings.Setting st : pc5.module("zoom").settings()) if (st.id.equals("level")) lvl = ((net.peregrine.client.core.settings.SliderSetting)st).value;
        check(lvl == 10f, "dragging the slider right sets the zoom level to 10x: " + lvl);
        m5.keyPressed(Menu.KEY_ESCAPE);
        // customize: opens the HUD editor with the style panel for that item
        int[] all = find("All@"); m5.mouseClicked(all[0]+1, all[1]+1, 0);
        texts.clear(); m5.render(d,0,0); gear = centerOfReflect(m5, "gear:fps"); m5.mouseClicked(gear[0], gear[1], 0);
        texts.clear(); m5.render(d,0,0); pc5.renderHud(d);
        int[] cust = centerOfReflect(m5, "customize"); m5.mouseClicked(cust[0], cust[1], 0);
        check(!m5.wantsShade(), "customize opens the HUD editor");
        pc5.renderHud(d); texts.clear(); m5.render(d,0,0);
        check(texts.stream().anyMatch(q->q.startsWith("Size@")), "...with the style panel open");
        m5.keyPressed(Menu.KEY_ESCAPE);
        pc5.shutdown(); String json5 = new String(Files.readAllBytes(cfg));
        check(json5.contains("\"version\": 2") && json5.contains("\"level\": 10.0") && json5.contains("\"dimension\": false"), "options saved");
        Peregrine pc6 = Peregrine.init(new P());
        boolean dimOn = true; for (net.peregrine.client.core.settings.Setting st : pc6.module("coords").settings()) if (st.id.equals("dimension")) dimOn = ((net.peregrine.client.core.settings.BoolSetting)st).value;
        check(!dimOn, "options load back");
        // ---- batch 4 HUD items
        for (String id : new String[]{"reach","combo","target","block_info","block_count","players","rotation","stopwatch","xp","chunk","light"}) pc6.module(id).setEnabled(true);
        playing = true; target = true; pc6.onMouseButton(0); pc6.onMouseButton(0); pc6.tick();
        texts.clear(); pc6.renderHud(d);
        check(texts.stream().anyMatch(q->q.startsWith("2.9 blocks@")), "reach of the last hit");
        check(texts.stream().anyMatch(q->q.startsWith("2 hits@")), "combo counts hits");
        check(texts.stream().anyMatch(q->q.startsWith("Zombie  7.0 hp  2.9m@")), "target name, health, distance");
        check(texts.stream().anyMatch(q->q.startsWith("Oak Planks@")), "block info");
        check(texts.stream().anyMatch(q->q.startsWith("128@")), "block counter");
        check(texts.stream().anyMatch(q->q.startsWith("23@")), "players online");
        check(texts.stream().anyMatch(q->q.startsWith("180.0 / 12.5@")), "rotation");
        check(texts.stream().anyMatch(q->q.startsWith("30  (25%)@")), "experience");
        check(texts.stream().anyMatch(q->q.startsWith("0, -1  [12, 10]@")), "chunk position");
        check(texts.stream().anyMatch(q->q.startsWith("0:00.0@")), "stopwatch at zero");
        hurt = 5; pc6.tick(); texts.clear(); pc6.renderHud(d);
        check(texts.stream().anyMatch(q->q.startsWith("0@")), "getting hit resets the combo");
        playing = false; target = false; hurt = 0;
        // ---- batch 5: anti base leak, crosshair, freelook, hit color, item physics
        Peregrine pc7 = Peregrine.init(new P());
        int r0 = reloads; pc7.module("anti_leak").setEnabled(true);
        check(Hooks.fixedRotation && Hooks.fixedOffset && Hooks.diamondBedrock, "anti base leak: one rotation, centred plants, diamond bedrock");
        check(reloads == r0 + 1, "and the chunks are redrawn");
        for (net.peregrine.client.core.settings.Setting st : pc7.module("anti_leak").settings()) if (st.id.equals("bedrock")) ((net.peregrine.client.core.settings.BoolSetting)st).value=false;
        pc7.tick(); check(!Hooks.diamondBedrock && Hooks.fixedRotation && reloads == r0 + 2, "changing an option redraws once");
        pc7.tick(); check(reloads == r0 + 2, "...and only once");
        pc7.module("anti_leak").setEnabled(false);
        check(!Hooks.fixedRotation && !Hooks.fixedOffset && reloads == r0 + 3, "off puts blocks back");
        pc7.module("crosshair").setEnabled(true); pc7.tick();
        List<int[]> rects = new ArrayList<>();
        Draw rd = new D(){ public void rect(int x,int y,int w,int h,int c){ rects.add(new int[]{x,y,w,h,c}); } };
        pc7.renderCrosshair(rd);
        check(Hooks.customCrosshair && rects.size() == 8, "cross crosshair: 4 arms + outlines (" + rects.size() + ")");
        check(rects.stream().anyMatch(q -> q[0] == 480 - 1 && q[3] == 7), "drawn in real pixels at the centre of the screen");
        pc7.module("freelook").setEnabled(true);
        altDown = true; camera = 0; pc7.tick();
        check(Hooks.freelook && camera == 1 && Hooks.camYaw == 180f && Hooks.camPitch == 12.5f, "freelook: Alt switches to third person, camera starts where you look");
        Hooks.turnCamera(100, 1000); check(Hooks.camYaw == 195f && Hooks.camPitch == 90f, "mouse turns only the camera (pitch clamped)");
        altDown = false; pc7.tick(); check(!Hooks.freelook && camera == 0, "letting go puts the camera back");
        pc7.module("hit_color").setEnabled(true); pc7.tick();
        check(hitColor == 0xB39B3BFF, "hit color: purple at 70%: " + Integer.toHexString(hitColor));
        pc7.module("hit_color").setEnabled(false); check(hitColor == 0, "off restores Minecraft's red");
        pc7.module("item_physics").setEnabled(true); check(Hooks.itemPhysics, "item physics switch");
        pc7.module("item_physics").setEnabled(false); check(!Hooks.itemPhysics, "item physics off");
        System.out.println("\nALL CORE TESTS PASSED");
    }
}
