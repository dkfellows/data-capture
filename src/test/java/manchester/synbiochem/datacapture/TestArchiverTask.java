package manchester.synbiochem.datacapture;

import static java.nio.file.Files.createTempDirectory;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.apache.commons.io.FileUtils.touch;
import static org.junit.Assert.*;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;

import manchester.synbiochem.datacapture.ArchiverTask.Entry;

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

	@Test
	public void testListFiles() throws Exception {
		File root = createTempDirectory(null).toFile();
		try {
			File dir = new File(root, "abc");
			dir.mkdir();

			assertEquals("abc", dir.getName());

			touch(new File(dir, "y.txt"));
			touch(new File(dir, "z.txt"));
			touch(new File(dir, "x.txt"));

			ArchiverTask t = new ArchiverTask(dir);
			t.listFiles(dir);

			ArrayList<String> l = new ArrayList<>();
			for (Entry e : t.getEntries())
				l.add(e.getName());
			Collections.sort(l);

			assertEquals("[abc/x.txt, abc/y.txt, abc/z.txt]", l.toString());
		} finally {
			deleteDirectory(root);
		}
	}
}
