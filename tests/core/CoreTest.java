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
        public void openScreen(Screen w){opened=w;} public boolean showing(Screen w){return false;}
        public String playerName(){return "Benjamin";} public String minecraftVersion(){return "1.21.1";}
        public long worldTime(){return 6000;} public String biome(){return "minecraft:dark_forest";} public float yaw(){return 180f;}
        public int food(){return 18;} public float saturation(){return 5.2f;} public int potionCount(){return 4;}
        public java.util.List<String> resourcePacks(){return java.util.Arrays.asList("Faithful 32x","Default");}
        public void setOption(Option o, boolean on){ if(on) optionsOn.add(o); else optionsOn.remove(o); }
        public int ping(){return ping;} public String serverAddress(){return server;} public long worldDay(){return 42;}
    }
    static java.util.Set<Platform.Option> optionsOn=new java.util.HashSet<>();
    static Platform.Screen opened; static int ping=-1; static String server=null;
    static List<String> texts = new ArrayList<>();
    static class D implements Draw {
        public void rect(int x,int y,int w,int h,int c){}
        public void text(String s,int x,int y,int c,boolean sh){texts.add(s+"@"+x+","+y);}
        public int width(String s){return s.length()*6;} public int lineHeight(){return 9;}
        public void item(Object st,int x,int y){} public void clip(int x,int y,int w,int h){} public void unclip(){}
        public void textScaled(String s,int x,int y,int c,float sc,boolean sh){texts.add(s+"@"+x+","+y);}
    }
    static void check(boolean c, String m){ if(!c) throw new AssertionError(m); System.out.println("ok  " + m); }
    static int[] find(String prefix){ for(String t: texts) if(t.startsWith(prefix)){ String[] xy=t.substring(t.lastIndexOf('@')+1).split(","); return new int[]{Integer.parseInt(xy[0]),Integer.parseInt(xy[1])}; } throw new AssertionError("not drawn: "+prefix); }
    public static void main(String[] a) throws Exception {
        cfg = Files.createTempDirectory("pc").resolve("config/peregrine-client.json");
        Peregrine pc = Peregrine.init(new P()); D d = new D(); Menu m = pc.menu();
        check(pc.modules().size()==36, "36 modules registered");
        for (String id : new String[]{"health","nether_coords","session","totems","arrows","durability_alert"}) check(pc.module(id)!=null, id+" exists");
        texts.clear(); pc.renderHud(d);
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
        m.keyPressed(Menu.KEY_ESCAPE); check(m.wantsShade(), "Esc leaves the HUD editor");
        // CPS
        for(int i=0;i<7;i++) pc.onMouseButton(0); pc.onMouseButton(1);
        check(pc.cps(0)==7 && pc.cps(1)==1, "CPS counts clicks");
        // save + reload
        pc.shutdown(); String json = new String(Files.readAllBytes(cfg));
        check(json.contains("\"cps\"") && json.contains("\"enabled\": true"), "settings saved");
        Peregrine pc2 = Peregrine.init(new P());
        check(pc2.module("cps").enabled() && Math.abs(((HudModule)pc2.module("fps")).fx-fps.fx)<1e-6, "settings load back");
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
        System.out.println("\nALL CORE TESTS PASSED");
    }
}
