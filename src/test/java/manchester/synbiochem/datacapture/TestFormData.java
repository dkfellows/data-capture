package manchester.synbiochem.datacapture;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestFormData {

	@Test
	public void testSimpleTwoFields() {
		MultipartFormData mfd = new MultipartFormData("abcde");
		mfd.addField("abc", "def");
		mfd.addField("bcd", "efg");
		mfd.build();
		assertEquals(
				"multipart/form-data; boundary=SynBioChemMetadataUploadFormToken10203040",
				mfd.contentType());
		assertEquals("239", mfd.length());
		assertEquals("--SynBioChemMetadataUploadFormToken10203040\r\n"
				+ "Content-Disposition: form-data; name=\"abc\"\r\n\r\ndef\r\n"
				+ "--SynBioChemMetadataUploadFormToken10203040\r\n"
				+ "Content-Disposition: form-data; name=\"bcd\"\r\n\r\nefg\r\n"
				+ "--SynBioChemMetadataUploadFormToken10203040--\r\n",
				new String(mfd.content()));
	}

	@Test
	public void testComplexTwoFields() {
		MultipartFormData mfd = new MultipartFormData("abcde");
		mfd.addField("abc", "def");
		mfd.addContent("bcd", "efg", "text/plain", "hij");
		mfd.build();
		assertEquals(
				"multipart/form-data; boundary=SynBioChemMetadataUploadFormToken10203040",
				mfd.contentType());
		assertEquals("316", mfd.length());
		assertEquals(
				"--SynBioChemMetadataUploadFormToken10203040\r\n"
						+ "Content-Disposition: form-data; name=\"abc\"\r\n\r\ndef\r\n"
						+ "--SynBioChemMetadataUploadFormToken10203040\r\n"
						+ "Content-Disposition: form-data; name=\"bcd\"; filename=\"efg\"\r\n"
						+ "Content-Type: text/plain\r\n"
						+ "Content-Transfer-Encoding: binary\r\n\r\n"
						+ "hij\r\n"
						+ "--SynBioChemMetadataUploadFormToken10203040--\r\n",
				new String(mfd.content()));
	}

	@Test
	public void testSeparatorSelection() {
		String content = "SynBioChemMetadataUploadFormToken10203040";
		MultipartFormData mfd = new MultipartFormData(content);
		mfd.addField("abc", content);
		mfd.build();
		assertEquals(
				"multipart/form-data; boundary=SynBioChemMetadataUploadFormToken10203041",
				mfd.contentType());
		assertEquals("181", mfd.length());
		assertEquals("--SynBioChemMetadataUploadFormToken10203041\r\n"
				+ "Content-Disposition: form-data; name=\"abc\"\r\n\r\n"
				+ "SynBioChemMetadataUploadFormToken10203040\r\n"
				+ "--SynBioChemMetadataUploadFormToken10203041--\r\n",
				new String(mfd.content()));
	}

	@Test(expected = IllegalStateException.class)
	public void testUsagePattern1() {
		String content = "SynBioChemMetadataUploadFormToken10203040";
		MultipartFormData mfd = new MultipartFormData(content);
		mfd.addField("abc", content);
		mfd.length();// Not available until after build() called
	}

	@Test(expected = IllegalStateException.class)
	public void testUsagePattern2() {
		String content = "SynBioChemMetadataUploadFormToken10203040";
		MultipartFormData mfd = new MultipartFormData(content);
		mfd.addField("abc", content);
		mfd.content();// Not available until after build() called
	}

	@Test
	public void testUsagePattern3() {
		String content = "SynBioChemMetadataUploadFormToken10203040";
		MultipartFormData mfd = new MultipartFormData(content);
		// The content type *is* available before the form is finalized, or even
		// any data is added...
		assertEquals(
				"multipart/form-data; boundary=SynBioChemMetadataUploadFormToken10203041",
				mfd.contentType());
	}
}
