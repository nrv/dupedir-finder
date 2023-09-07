package name.herve.dupdir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import name.herve.dupedir.DupedirFinder;
import name.herve.dupedir.Log;
import name.herve.dupedir.DupedirFinder.DuplicateDir;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DupedirTest {

	private List<DuplicateDir> findDuplicates(DupedirFinder ddf, boolean aggregateHierarchy) {
		List<DuplicateDir> duplicates = ddf.findDuplicates(aggregateHierarchy);
		if (duplicates != null) {
			for (DuplicateDir dup : duplicates) {
				Log.log(" . " + dup);
			}
		}
		return duplicates;
	}

	private DuplicateDir getDuplicate(List<DuplicateDir> candidates, String dir1, String dir2) {
		for (DuplicateDir dd : candidates) {
			if (dd.getDir1().getPath().toString().equals(dir1) && dd.getDir2().getPath().toString().equals(dir2)) {
				return dd;
			}
			if (dd.getDir1().getPath().toString().equals(dir2) && dd.getDir2().getPath().toString().equals(dir1)) {
				return dd;
			}
		}
		return null;
	}

	private DupedirFinder init(String file, int maxNbDirForFile, int minNbCommonFiles) {
		BufferedReader r = null;
		try {
			URL resource = this.getClass().getClassLoader().getResource(file);
			String content = new String(Files.readAllBytes(Paths.get(resource.toURI())));
			r = new BufferedReader(new StringReader(content));

			DupedirFinder ddf = new DupedirFinder();
			ddf.initScan();
			ddf.setMaxNbDirForFile(maxNbDirForFile);
			ddf.setMinNbCommonFiles(minNbCommonFiles);
			String line = null;
			while ((line = r.readLine()) != null) {
				line = line.strip();
				if (!line.isBlank() && !line.startsWith("#")) {
					Path path = Paths.get(line).toAbsolutePath();
					ddf.addFileToIndex(path);
				}
			}
			ddf.hierarchyStats();
			return ddf;
		} catch (IOException | URISyntaxException e) {
			Assert.fail(file + " | " + e.getClass().getName() + " : " + e.getMessage());
			return null;
		} finally {
			if (r != null) {
				try {
					r.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
	}

	@Test
	public void test01() {
		DupedirFinder ddf = init("test01.txt", 100, 1);
		List<DuplicateDir> duplicates = findDuplicates(ddf, false);
		Assert.assertNotNull(duplicates);
		Assert.assertEquals(0, duplicates.size());
	}

	@Test
	public void test02() {
		DupedirFinder ddf = init("test02.txt", 100, 1);
		List<DuplicateDir> duplicates = findDuplicates(ddf, false);
		Assert.assertNotNull(duplicates);
		Assert.assertEquals(1, duplicates.size());
		DuplicateDir dd = getDuplicate(duplicates, "/root/dir1", "/root/dir3");
		Assert.assertNotNull(dd);
		Assert.assertEquals(1, dd.getNbCommonFiles());
	}

	@Test
	public void test03() {
		DupedirFinder ddf = init("test03.txt", 100, 1);
		List<DuplicateDir> duplicates = findDuplicates(ddf, false);
		Assert.assertNotNull(duplicates);
		Assert.assertEquals(1, duplicates.size());
		DuplicateDir dd = getDuplicate(duplicates, "/root/dir1", "/root/dir3");
		Assert.assertNotNull(dd);
		Assert.assertEquals(4, dd.getNbCommonFiles());
		Assert.assertEquals(1., dd.getMaxPct(), 1e-5);
	}

	@Test
	public void test04() {
		DupedirFinder ddf = init("test04.txt", 100, 1);
		List<DuplicateDir> duplicates = findDuplicates(ddf, false);
		Assert.assertNotNull(duplicates);
		Assert.assertEquals(2, duplicates.size());
		DuplicateDir dd1 = getDuplicate(duplicates, "/root/dir1", "/root/dir3");
		Assert.assertNotNull(dd1);
		Assert.assertEquals(4, dd1.getNbCommonFiles());
		Assert.assertEquals(1., dd1.getMaxPct(), 1e-5);
		DuplicateDir dd2 = getDuplicate(duplicates, "/root/dir2", "/root/dir4");
		Assert.assertNotNull(dd2);
		Assert.assertEquals(1, dd2.getNbCommonFiles());
		Assert.assertEquals(0.5, dd2.getMaxPct(), 1e-5);
	}

	@Test
	public void test10() {
		DupedirFinder ddf = init("test10.txt", 100, 1);
		List<DuplicateDir> duplicates = findDuplicates(ddf, false);
		Assert.assertNotNull(duplicates);
		Assert.assertEquals(1, duplicates.size());
		DuplicateDir dd1 = getDuplicate(duplicates, "/root/aaa/111", "/root/bbb");
		Assert.assertNotNull(dd1);
		Assert.assertEquals(1, dd1.getNbCommonFiles());
		DuplicateDir ddh = getDuplicate(duplicates, "/root/aaa", "/root/bbb");
		Assert.assertNull(ddh);
		
		ddf = init("test10.txt", 100, 1);
		duplicates = findDuplicates(ddf, true);
		Assert.assertNotNull(duplicates);
		Assert.assertEquals(2, duplicates.size());
		dd1 = getDuplicate(duplicates, "/root/aaa/111", "/root/bbb");
		Assert.assertNotNull(dd1);
		Assert.assertEquals(1, dd1.getNbCommonFiles());
		Assert.assertEquals(1, dd1.getNbCommonFilesHierarchy());
		ddh = getDuplicate(duplicates, "/root/aaa", "/root/bbb");
		Assert.assertNotNull(ddh);
		Assert.assertEquals(0, ddh.getNbCommonFiles());
		Assert.assertEquals(1, ddh.getNbCommonFilesHierarchy());
	}
	
	@Test
	public void test11() {
		DupedirFinder ddf = init("test11.txt", 100, 1);
		List<DuplicateDir> duplicates = findDuplicates(ddf, false);
		Assert.assertNotNull(duplicates);
		Assert.assertEquals(2, duplicates.size());
		DuplicateDir dd1 = getDuplicate(duplicates, "/root/aaa/111", "/root/bbb/111");
		Assert.assertNotNull(dd1);
		Assert.assertEquals(1, dd1.getNbCommonFiles());
		DuplicateDir dd2 = getDuplicate(duplicates, "/root/aaa/222", "/root/bbb/222");
		Assert.assertNotNull(dd2);
		Assert.assertEquals(1, dd2.getNbCommonFiles());
		DuplicateDir ddh = getDuplicate(duplicates, "/root/aaa", "/root/bbb");
		Assert.assertNull(ddh);
		
		ddf = init("test11.txt", 100, 1);
		duplicates = findDuplicates(ddf, true);
		Assert.assertNotNull(duplicates);
		Assert.assertEquals(7, duplicates.size());
		dd1 = getDuplicate(duplicates, "/root/aaa/111", "/root/bbb/111");
		Assert.assertNotNull(dd1);
		Assert.assertEquals(1, dd1.getNbCommonFiles());
		dd2 = getDuplicate(duplicates, "/root/aaa/222", "/root/bbb/222");
		Assert.assertNotNull(dd2);
		Assert.assertEquals(1, dd2.getNbCommonFiles());
		ddh = getDuplicate(duplicates, "/root/aaa", "/root/bbb");
		Assert.assertNotNull(ddh);
		Assert.assertEquals(2, ddh.getNbCommonFilesHierarchy());
	}
}
