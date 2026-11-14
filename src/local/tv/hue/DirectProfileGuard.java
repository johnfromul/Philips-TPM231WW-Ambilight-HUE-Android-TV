package local.tv.hue;

/** Validate the target first; never commit while synchronization or recovery is active. */
final class DirectProfileGuard {
 interface Gate {boolean busy()throws Exception;}
 interface Check {void verify()throws Exception;}
 interface Commit {void write()throws Exception;}
 static void select(Gate gate,Check check,Commit commit)throws Exception{
  if(gate.busy())throw new IllegalStateException("Synchronization or recovery is active");
  check.verify();
  if(gate.busy())throw new IllegalStateException("Synchronization or recovery became active");
  commit.write();
 }
}
