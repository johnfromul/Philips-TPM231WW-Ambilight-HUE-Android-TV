package local.tv.hue;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class BackupCipher {
 static final int MAX_FILE_BYTES=4*1024*1024;
 private static final byte[] HEADER={'A','M','B','I','H','U','E',1};
 private static final int IV_BYTES=12;
 // A common format key keeps exports readable after reinstall. It is recoverable from the APK.
 private static final String FORMAT_KEY="4082d84dadfe247cf52412390c7e0df0b0c05f0e6d7383550a5e566cdff55980";

 private BackupCipher(){}

 static byte[] encrypt(byte[] payload)throws GeneralSecurityException,IOException{
  if(payload.length>MAX_FILE_BYTES-HEADER.length-IV_BYTES-16)throw new IOException("Backup too large");
  byte[] iv=new byte[IV_BYTES];
  new SecureRandom().nextBytes(iv);
  byte[] encrypted=cipher(Cipher.ENCRYPT_MODE,iv).doFinal(payload);
  byte[] file=Arrays.copyOf(HEADER,HEADER.length+iv.length+encrypted.length);
  System.arraycopy(iv,0,file,HEADER.length,iv.length);
  System.arraycopy(encrypted,0,file,HEADER.length+iv.length,encrypted.length);
  return file;
 }

 static byte[] decrypt(byte[] file)throws GeneralSecurityException,IOException{
  if(file.length<HEADER.length+IV_BYTES+16||file.length>MAX_FILE_BYTES
     ||!Arrays.equals(HEADER,Arrays.copyOf(file,HEADER.length)))throw new IOException("Invalid backup header");
  int offset=HEADER.length+IV_BYTES;
  return cipher(Cipher.DECRYPT_MODE,Arrays.copyOfRange(file,HEADER.length,offset))
      .doFinal(file,offset,file.length-offset);
 }

 private static Cipher cipher(int mode,byte[] iv)throws GeneralSecurityException{
  byte[] key=new byte[32];
  for(int i=0;i<key.length;i++)key[i]=(byte)Integer.parseInt(FORMAT_KEY.substring(2*i,2*i+2),16);
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
  cipher.updateAAD(HEADER);
  return cipher;
 }
}
