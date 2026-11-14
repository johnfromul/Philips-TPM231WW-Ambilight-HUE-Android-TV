import pathlib
import subprocess
import tempfile
import unittest


class DirectProfileGuardTest(unittest.TestCase):
    def test_switch_is_transactional_and_blocked_during_session_or_recovery(self):
        root = pathlib.Path(__file__).resolve().parents[1]
        harness = '''package local.tv.hue;
public class GuardTest {
 static String selected="v2";
 static void eq(String expected) {if(!expected.equals(selected))throw new AssertionError(selected);}
 public static void main(String[] args)throws Exception {
  try { DirectProfileGuard.select(()->true,()->{},()->selected="pro"); throw new AssertionError(); } catch(IllegalStateException expected){} eq("v2");
  try { DirectProfileGuard.select(()->false,()->{throw new Exception("wrong bridge or credential");},()->selected="pro"); throw new AssertionError(); } catch(Exception expected){} eq("v2");
  final int[] checks={0};
  try { DirectProfileGuard.select(()->++checks[0]>1,()->{},()->selected="pro"); throw new AssertionError(); } catch(IllegalStateException expected){} eq("v2");
  DirectProfileGuard.select(()->false,()->{},()->selected="pro"); eq("pro");
  DirectProfileGuard.select(()->false,()->{},()->selected="v2"); eq("v2");
 }
}'''
        with tempfile.TemporaryDirectory() as tmp:
            source = pathlib.Path(tmp) / 'GuardTest.java'
            source.write_text(harness)
            subprocess.run(['javac', '-d', tmp, str(root / 'src/local/tv/hue/DirectProfileGuard.java'), str(source)], check=True)
            subprocess.run(['java', '-cp', tmp, 'local.tv.hue.GuardTest'], check=True)


if __name__ == '__main__':
    unittest.main()
