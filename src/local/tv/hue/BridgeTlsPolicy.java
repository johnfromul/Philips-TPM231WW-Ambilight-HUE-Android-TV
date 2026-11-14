package local.tv.hue;
import java.security.cert.CertificateException;
final class BridgeTlsPolicy {
 static void verify(String expected,String actual,boolean publicConfig)throws CertificateException{
  if(actual==null||!actual.matches("[a-f0-9]{64}"))throw new CertificateException("Invalid certificate fingerprint");
  if(expected.isEmpty()){if(!publicConfig)throw new CertificateException("Certificate trust not approved");}
  else if(!expected.equals(actual))throw new CertificateException("Bridge certificate changed");
 }
 static boolean legacyModel(String model){return "BSB002".equals(model);}
}
