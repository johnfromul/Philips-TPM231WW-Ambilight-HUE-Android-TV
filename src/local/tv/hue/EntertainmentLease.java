package local.tv.hue;

/** Explicit ownership boundary for an Entertainment area. All network methods must be bounded. */
final class EntertainmentLease {
 enum Owner { FREE, OURS, OTHER, UNKNOWN }
 interface Journal {
  boolean pending() throws Exception;
  void record(String bridgeId,String areaId) throws Exception;
  boolean matches(String bridgeId,String areaId) throws Exception;
  void confirm() throws Exception;
  boolean confirmed() throws Exception;
  void clear() throws Exception;
 }
 interface Bridge {
  Owner owner(String areaId) throws Exception;
  void start(String areaId) throws Exception;
  void stop(String areaId) throws Exception;
 }
 interface Stream {
  void open() throws Exception;
  void close() throws Exception;
 }
 private final String bridgeId,areaId; private final Journal journal; private final Bridge bridge; private final Stream stream;
 private boolean ready;
 EntertainmentLease(String bridgeId,String areaId,Journal journal,Bridge bridge,Stream stream){
  if(bridgeId==null||bridgeId.isEmpty()||areaId==null||areaId.isEmpty()||journal==null||bridge==null||stream==null)throw new IllegalArgumentException("Missing session parameter");
  this.bridgeId=bridgeId;this.areaId=areaId;this.journal=journal;this.bridge=bridge;this.stream=stream;
 }
 synchronized void start() throws Exception {
  if(ready||journal.pending())throw new IllegalStateException("Pending entertainment recovery");
  if(bridge.owner(areaId)!=Owner.FREE)throw new IllegalStateException("Entertainment area is not free");
  journal.record(bridgeId,areaId); // Persist before the request; even a timeout may have claimed it.
  try {
   bridge.start(areaId);
   if(bridge.owner(areaId)!=Owner.OURS)throw new IllegalStateException("Entertainment ownership unconfirmed");
   journal.confirm(); // A confirmed claim cannot later appear from this completed request.
   stream.open();ready=true;
  } catch(Exception failure){try{recover();}catch(Exception recovery){failure.addSuppressed(recovery);}throw failure;}
 }
 synchronized void stop() throws Exception {if(ready||journal.pending())recover();}
 synchronized void recover() throws Exception {
  boolean wasReady=ready;
  ready=false;
  if(!journal.pending())return;
  if(!journal.matches(bridgeId,areaId))throw new IllegalStateException("Recovery belongs to another bridge or area");
  Exception failure=null;
  try{stream.close();}catch(Exception e){failure=e;}
  Owner owner;
  try{owner=bridge.owner(areaId);}catch(Exception e){if(failure!=null)e.addSuppressed(failure);throw e;}
  if(owner==Owner.OTHER||owner==Owner.UNKNOWN){IllegalStateException e=new IllegalStateException("Cannot verify area ownership; recovery retained");if(failure!=null)e.addSuppressed(failure);throw e;}
  if(owner==Owner.FREE&&!wasReady&&!journal.confirmed()){IllegalStateException e=new IllegalStateException("Unconfirmed start may still complete; recovery retained");if(failure!=null)e.addSuppressed(failure);throw e;}
  if(owner==Owner.OURS)try{bridge.stop(areaId);if(bridge.owner(areaId)!=Owner.FREE)throw new IllegalStateException("Area stop not confirmed");}catch(Exception e){if(failure==null)failure=e;else failure.addSuppressed(e);}
  if(failure!=null)throw failure;
  journal.clear();
 }
}
