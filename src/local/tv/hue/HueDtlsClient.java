package local.tv.hue;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Arrays;
import local.tv.hue.crypto.bc.tls.CipherSuite;
import local.tv.hue.crypto.bc.tls.DTLSClientProtocol;
import local.tv.hue.crypto.bc.tls.DTLSTransport;
import local.tv.hue.crypto.bc.tls.PSKTlsClient;
import local.tv.hue.crypto.bc.tls.ProtocolVersion;
import local.tv.hue.crypto.bc.tls.UDPTransport;
import local.tv.hue.crypto.bc.tls.crypto.impl.bc.BcTlsCrypto;

/** DTLS 1.2 PSK transport only. Never log, serialize, or put credentials on a command line. */
final class HueDtlsClient implements Closeable {
 private DTLSTransport transport;
 private DatagramSocket socket;

 synchronized void connect(InetAddress address,int port,byte[] identity,byte[] psk) throws IOException {
  if(transport!=null||socket!=null)throw new IOException("Already connected");
  if(address==null||port<1||port>65535||identity==null||identity.length==0||identity.length>128||psk==null||psk.length!=16)throw new IllegalArgumentException("Invalid DTLS parameters");
  final byte[] identityCopy=identity.clone(),keyCopy=psk.clone();
  try {
   socket=new DatagramSocket();socket.connect(address,port);
   PSKTlsClient client=new PSKTlsClient(new BcTlsCrypto(new SecureRandom()),identityCopy,keyCopy){
    @Override protected ProtocolVersion[] getSupportedVersions(){return new ProtocolVersion[]{ProtocolVersion.DTLSv12};}
    @Override protected int[] getSupportedCipherSuites(){return new int[]{CipherSuite.TLS_PSK_WITH_AES_128_GCM_SHA256};}
    @Override public int getHandshakeTimeoutMillis(){return 8000;}
   };
   transport=new DTLSClientProtocol().connect(client,new UDPTransport(socket,1280));
  } catch(IOException|RuntimeException e){closeQuietly();throw e;}
  finally{Arrays.fill(identityCopy,(byte)0);Arrays.fill(keyCopy,(byte)0);}
 }

 synchronized void send(byte[] frame) throws IOException {
  if(transport==null)throw new IOException("DTLS not connected");
  if(frame==null||frame.length<59||frame.length>192)throw new IllegalArgumentException("Invalid entertainment frame");
  try{transport.send(frame,0,frame.length);}catch(IOException e){closeQuietly();throw e;}
 }
 @Override public synchronized void close() throws IOException {
  IOException error=null;try{if(transport!=null)transport.close();}catch(IOException e){error=e;}finally{transport=null;if(socket!=null)socket.close();socket=null;}
  if(error!=null)throw error;
 }
 private void closeQuietly(){try{close();}catch(IOException ignored){}}
}
