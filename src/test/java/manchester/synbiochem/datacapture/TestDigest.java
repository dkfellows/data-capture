package manchester.synbiochem.datacapture;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestDigest {
	private static final String abcMD5 = "900150983CD24FB0D6963F7D28E17F72";

	@Test
	public void generateMD5FromString() {
		Digest d = new Digest(Algorithm.MD5);
		d.update("abc");
		assertEquals(abcMD5, d.toString());
		// And check we get the same value when we read it again
		assertEquals(abcMD5, d.toString());
	}

	@Test
	public void generateMD5FromBytes() {
		Digest d = new Digest(Algorithm.MD5);
		d.update(new byte[] { 97, 98, 99 });
		assertEquals(abcMD5, d.toString());
	}

	@Test
	public void generateMD5FromByteRange() {
		Digest d = new Digest(Algorithm.MD5);
		d.update(new byte[] { 97, 98, 99, 100 }, 3);
		assertEquals(abcMD5, d.toString());
	}

	@Test
	public void onlyGenerateOnce() {
		Digest d = new Digest(Algorithm.MD5);
		d.update("abc");
		d.toString();
		d.update("def");// This should be ignored
		assertEquals(abcMD5, d.toString());
	}

	@Test
	public void generateInParts() {
		Digest d = new Digest(Algorithm.MD5);
		d.update("ab");
		d.update("c");
		assertEquals(abcMD5, d.toString());
	}

	private static final String abEuroMD5 = "C0019B3480EE849D6DB33C74F1676A31";

	@Test
	public void generateUnicodeViaUTF8() {
		Digest d = new Digest(Algorithm.MD5);
		d.update("ab\u20ac");
		assertEquals(abEuroMD5, d.toString());
	}

	private static final String abcSHA1 = "A9993E364706816ABA3E25717850C26C9CD0D89D";

	@Test
	public void generateSHA1() {
		Digest d = new Digest(Algorithm.SHA1);
		d.update("abc");
		assertEquals(abcSHA1, d.toString());
	}
}
