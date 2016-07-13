package manchester.synbiochem.datacapture;

import static org.junit.Assert.*;

import java.net.URI;

import org.junit.Test;

public class TestArchiverTask {

	@Test
	public void testResolve() {
		URI base = URI.create("http://seek.org/example/");
		assertEquals(URI.create("http://seek.org/example/abc"),
				ArchiverTask.resolveToURI(base, "abc"));
		assertEquals(URI.create("http://seek.org/example/abc/def"),
				ArchiverTask.resolveToURI(base, "abc/def"));
		assertEquals(URI.create("http://seek.org/example/abc.def"),
				ArchiverTask.resolveToURI(base, "abc.def"));
		assertEquals(URI.create("http://seek.org/example/abc%40def"),
				ArchiverTask.resolveToURI(base, "abc@def"));
		assertEquals(URI.create("http://seek.org/example/abc+def"),
				ArchiverTask.resolveToURI(base, "abc def"));
		assertEquals(URI.create("http://seek.org/example/abc+def+ghi/jkl.mn"),
				ArchiverTask.resolveToURI(base, "abc def ghi/jkl.mn"));
		assertEquals(URI.create("http://seek.org/example/abc+def%E2%82%AC.ghi"),
				ArchiverTask.resolveToURI(base, "abc def\u20ac.ghi"));
	}
}
