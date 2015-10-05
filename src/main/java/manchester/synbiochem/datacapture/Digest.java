package manchester.synbiochem.datacapture;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Simplifying wrapper around the message digest classes.
 * 
 * @author Donal Fellows
 */
class Digest {
	/** Hex digits */
	static final char[] HEX = new char[] { '0', '1', '2', '3', '4', '5', '6',
		'7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	private static final Charset UTF8 = Charset.forName("UTF-8");
	
	private MessageDigest md;
	private String hex;

	/**
	 * Create a digester.
	 * 
	 * @param algorithm
	 *            The algorithm that the digester is to use.
	 */
	public Digest(Algorithm algorithm) {
		try {
			md = MessageDigest.getInstance(algorithm.name());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException(
					"unexpected failure to configure message digest", e);
		}
		hex = null;
	}

	/**
	 * Add a buffer into the digest.
	 * 
	 * @param buffer
	 *            The buffer to add.
	 * @param len
	 *            The number of bytes held in the buffer. The sequence of
	 *            bytes in the buffer is assumed to start at zero.
	 * @return This
	 */
	public Digest update(byte[] buffer, int len) {
		md.update(buffer, 0, len);
		return this;
	}

	/**
	 * Add a buffer into the digest.
	 * 
	 * @param buffer
	 *            The buffer to add.
	 * @return This
	 */
	public Digest update(byte[] buffer) {
		md.update(buffer, 0, buffer.length);
		return this;
	}

	/**
	 * Add a string into the digest.
	 * 
	 * @param string
	 *            The string to add.
	 * @return This
	 */
	public Digest update(String string) {
		return update(string.getBytes(UTF8));
	}

	@Override
	public String toString() {
		if (hex != null)
			return hex;
		StringBuilder sb = new StringBuilder();
		for (byte b : md.digest()) {
			int un = (((int) b) >> 4) & 0xf;
			int ln = b & 0xf;
			sb.append(HEX[un]).append(HEX[ln]);
		}
		return hex = sb.toString();
	}
}

enum Algorithm {
	SHA1 {
		@Override
		public String toString() {
			return "SHA-1";
		}
	},
	MD5
}
