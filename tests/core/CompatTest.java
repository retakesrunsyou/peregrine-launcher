import net.peregrine.client.core.Compat;
public class CompatTest { public static void main(String[] a){
  String[][] f={{"0","South"},{"90","West"},{"180","North"},{"-90","East"},{"270","East"},{"44","South"},{"46","West"},{"-180","North"},{"359","South"}};
  for(String[] t:f){ String got=Compat.facing(Float.parseFloat(t[0])); if(!got.equals(t[1])) throw new AssertionError(t[0]+" -> "+got); }
  if(!Compat.keyId("ResourceKey[minecraft:worldgen/biome / minecraft:dark_forest]").equals("minecraft:dark_forest")) throw new AssertionError("keyId");
  System.out.println("compat ok"); } }
