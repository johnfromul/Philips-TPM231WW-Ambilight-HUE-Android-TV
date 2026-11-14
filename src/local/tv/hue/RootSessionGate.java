package local.tv.hue;

/** Serializes root sync and recovery within the app process. */
final class RootSessionGate {
 private static Object owner;
 static synchronized void claim(Object token){if(token==null||owner!=null)throw new IllegalStateException("Root session is busy");owner=token;}
 static synchronized void release(Object token){if(owner==token)owner=null;}
 static synchronized boolean busy(){return owner!=null;}
}
