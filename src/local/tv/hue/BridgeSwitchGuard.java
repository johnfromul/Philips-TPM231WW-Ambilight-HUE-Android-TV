package local.tv.hue;
import java.io.IOException;

/** Bounded check for a native service overwriting the selected bridge identity. */
final class BridgeSwitchGuard {
 interface Probe { String address() throws Exception; String identity() throws Exception; void pause(long millis) throws Exception; void event(int attempt,boolean addressMatch,boolean identityMatch); }
 static final class Mismatch extends IOException { Mismatch(){super("Native service replaced selected bridge");} }
 static void verify(Probe probe,String expectedAddress,String expectedIdentity)throws Exception{
  for(int attempt=0;attempt<5;attempt++){
   if(attempt>0)probe.pause(attempt==4?4000:1500);
   boolean addressMatch=expectedAddress.equals(probe.address());
   boolean identityMatch=expectedIdentity.equals(probe.identity());
   probe.event(attempt,addressMatch,identityMatch);
   if(!addressMatch||!identityMatch)throw new Mismatch();
  }
 }
}
