package name.herve.dupedir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class DupedirFinder {
	public class Dir {
		private int id;
		private Path path;
		private long nbFiles;
		private long nbFilesHierarchy;
		private List<Dir> subDirs;
		private Dir parent;

		public Dir(int id, Path path) {
			super();
			this.id = id;
			this.path = path;
			nbFiles = -1;
			nbFilesHierarchy = -1;
		}

		public void addNbFilesHierarchy(long nbFilesHierarchy) {
			this.nbFilesHierarchy += nbFilesHierarchy;
		}

		public int getId() {
			return id;
		}

		public long getNbFiles() {
			return nbFiles;
		}

		public long getNbFilesHierarchy() {
			return nbFilesHierarchy;
		}

		public Dir getParent() {
			return parent;
		}

		public Path getPath() {
			return path;
		}

		public List<Dir> getSubDirs() {
			return subDirs;
		}

		public void setNbFiles(long nbFiles) {
			this.nbFiles = nbFiles;
		}

		public void setNbFilesHierarchy(long nbFilesHierarchy) {
			this.nbFilesHierarchy = nbFilesHierarchy;
		}

		public void setParent(Dir parent) {
			this.parent = parent;
		}

		public void setSubDirs(List<Dir> subDirs) {
			this.subDirs = subDirs;
		}

		@Override
		public String toString() {
			return "[" + id + "] [" + (parent == null) + "] [" + DECF.format(nbFiles) + " / " + DECF.format(nbFilesHierarchy) + "] " + path.toString();
		}
	}

	public class DuplicateDir {
		private Dir dir1;
		private Dir dir2;
		private int nbCommonFiles;
		private int nbCommonFilesHierarchy;
		private float maxPct;
		private float score;

		public DuplicateDir(Dir dir1, Dir dir2) {
			super();
			this.dir1 = dir1;
			this.dir2 = dir2;
			nbCommonFiles = 0;
			nbCommonFilesHierarchy = 0;
		}

		private void computeScores(boolean aggregateHierarchy) {
			if (aggregateHierarchy) {
				maxPct = Math.min(dir1.getNbFilesHierarchy(), dir2.getNbFilesHierarchy());
				if (maxPct > 0) {
					maxPct = nbCommonFilesHierarchy / maxPct;
				}
				score = (float) Math.log10(nbCommonFilesHierarchy) + maxPct;
			} else {
				maxPct = Math.min(dir1.getNbFiles(), dir2.getNbFiles());
				if (maxPct > 0) {
					maxPct = nbCommonFiles / maxPct;
				}
				score = (float) Math.log10(nbCommonFiles) + maxPct;
			}
		}

		public Dir getDir1() {
			return dir1;
		}

		public Dir getDir2() {
			return dir2;
		}

		public float getMaxPct() {
			return maxPct;
		}

		public int getNbCommonFiles() {
			return nbCommonFiles;
		}

		public int getNbCommonFilesHierarchy() {
			return nbCommonFilesHierarchy;
		}

		public float getScore() {
			return score;
		}

		public void incNbCommonFiles() {
			nbCommonFiles++;
		}

		public void addNbCommonFilesHierarchy(int nbCommonFilesHierarchy) {
			this.nbCommonFilesHierarchy += nbCommonFilesHierarchy;
		}

		@Override
		public String toString() {
			return "{" + PCTF.format(score) + "} " + PCTF.format(100 * maxPct) + "% [" 
					+ DECF.format(nbCommonFiles) + " / " + DECF.format(nbCommonFilesHierarchy) + "] - [" 
					+ DECF.format(dir1.getNbFiles()) + " / " + DECF.format(dir1.getNbFilesHierarchy()) + "] " + dir1.getPath() + " - [" 
					+ DECF.format(dir2.getNbFiles()) + " / " + DECF.format(dir2.getNbFilesHierarchy()) + "] " + dir2.getPath();
		}
	}

	private static DecimalFormat DECF = new DecimalFormat("###,###");
	private static DecimalFormat PCTF = new DecimalFormat("00.00");

	public static void main(String[] args) {
		new DupedirFinder().start(args);
	}

	private Options options;

	private HashMap<String, List<Dir>> fileToDirs;
	private int idGenerator;
	private TreeMap<String, Dir> allDirs;
	private TreeSet<String> allFiles;
	private Counter<String> nbFilePerDir;

	private int minNbCommonFiles = 3;
	private int maxNbDirForFile = 50;

	public DupedirFinder() {
		super();
	}

	public void addFileToIndex(Path file) {
		String name = file.getFileName().toString();
		List<Dir> dirs = fileToDirs.get(name);
		if (dirs == null) {
			dirs = new ArrayList<>();
			fileToDirs.put(name, dirs);
		}
		Path dirPath = file.getParent();
		if (dirPath != null) {
			Dir dir = getDir(dirPath);
			if (dirs.add(dir)) {
				nbFilePerDir.add(dir.getPath().toString());
			}

			dirPath = dirPath.getParent();
			while (dirPath != null) {
				dir = getDir(dirPath);
				dirPath = dirPath.getParent();
			}
		}
	}

	private DuplicateDir getDuplicate(Map<String, DuplicateDir> candidates, Dir diri, Dir dirj) {
		int id1 = diri.getId();
		int id2 = dirj.getId();
		if (id1 > id2) {
			id1 = id2;
			id2 = diri.getId();
		}
		String k = id1 + "-" + id2;
		DuplicateDir dup = candidates.get(k);
		if (dup == null) {
			dup = new DuplicateDir(diri, dirj);
			candidates.put(k, dup);
		}
		return dup;
	}

	public List<DuplicateDir> findDuplicates(boolean aggregateHierarchy) {
		Log.log("Finding duplicates over " + DECF.format(fileToDirs.size()) + " file names in " + DECF.format(allDirs.size()) + " directories");
		Map<String, DuplicateDir> candidates = new HashMap<>();

		for (Entry<String, List<Dir>> e : fileToDirs.entrySet()) {
			List<Dir> dirs = e.getValue();
			if ((dirs.size() > 1) && (dirs.size() <= maxNbDirForFile)) {
				// Log.log("[" + e.getValue().size() + "] " + e.getKey());
				for (int i = 0; i < (dirs.size() - 1); i++) {
					Dir diri = dirs.get(i);
					for (int j = i + 1; j < dirs.size(); j++) {
						Dir dirj = dirs.get(j);
						DuplicateDir dup = getDuplicate(candidates, diri, dirj);
						dup.incNbCommonFiles();
					}
				}
			}
		}

		if (aggregateHierarchy) {
			List<DuplicateDir> initialCandidates = new ArrayList<>(candidates.values());
			for (DuplicateDir dup : initialCandidates) {
				Dir d1 = dup.getDir1();
				while (d1 != null) {
					Dir d2 = dup.getDir2();
					while (d2 != null) {
						if (d2.getPath().startsWith(d1.getPath()) || d1.getPath().startsWith(d2.getPath())) {
							d2 = null;
						} else {
							DuplicateDir duph = getDuplicate(candidates, d1, d2);
							duph.addNbCommonFilesHierarchy(dup.getNbCommonFiles());
							d2 = d2.getParent();
						}
					}
					d1 = d1.getParent();
				}
			}
		}

		List<DuplicateDir> sorted = new ArrayList<>();
		for (DuplicateDir dup : candidates.values()) {
			if ((!aggregateHierarchy && dup.getNbCommonFiles() >= minNbCommonFiles) || (aggregateHierarchy && dup.getNbCommonFilesHierarchy() >= minNbCommonFiles)) {
				dup.computeScores(aggregateHierarchy);
				sorted.add(dup);
			}
		}

		Collections.sort(sorted, new Comparator<DuplicateDir>() {

			@Override
			public int compare(DuplicateDir o1, DuplicateDir o2) {
				int cmp = (int) Math.signum(o2.getScore() - o1.getScore());
				return cmp;
			}
		});

		return sorted;
	}

	private Dir getDir(Path p) {
		Dir dir = allDirs.get(p.toString());
		if (dir == null) {
			dir = new Dir(idGenerator++, p);
			dir.setSubDirs(new ArrayList<>());
			allDirs.put(p.toString(), dir);
		}
		return dir;
	}

	public int getMaxNbDirForFile() {
		return maxNbDirForFile;
	}

	public int getMinNbCommonFiles() {
		return minNbCommonFiles;
	}

	private void help(boolean isError, String message) {
		if (message != null) {
			Log.log(isError, message);
		}
		HelpFormatter formatter = new HelpFormatter();
		StringWriter w = new StringWriter();
		PrintWriter pw = new PrintWriter(w);
		formatter.printHelp(pw, 150, getClass().getName(), "", options, 3, 3, "");
		for (String line : w.toString().split("\\r?\\n")) {
			Log.log(isError, line);
		}
	}

	private void hierarchyAccumulate(Dir d) {
		for (Dir s : d.subDirs) {
			hierarchyAccumulate(s);
		}
		if (d.getParent() != null) {
			d.getParent().addNbFilesHierarchy(d.getNbFilesHierarchy());
		}
	}

	public void hierarchyStats() {
		List<Dir> rootDirs = new ArrayList<>();

		for (Dir dir : allDirs.values()) {
			dir.setNbFiles(nbFilePerDir.getCount(dir.getPath().toString()));
			dir.setNbFilesHierarchy(dir.getNbFiles());
			Path parent = dir.getPath().getParent();
			if (parent != null) {
				Dir parentDir = getDir(parent);
				parentDir.getSubDirs().add(dir);
				dir.setParent(parentDir);
			} else {
				rootDirs.add(dir);
			}
		}

		for (Dir d : rootDirs) {
			hierarchyAccumulate(d);
		}

		// for (Dir dir : allDirs.values()) {
		// Log.log("" + dir);
		// }
	}

	private void initOptions() {
		options = new Options();
		options.addOption("h", "help", false, "print this help");

		OptionGroup action = new OptionGroup();
		action.addOption(new Option("s", "scan", false, "launch scan"));
		action.addOption(new Option("o", "list", true, "where to store files list"));
		action.addOption(new Option("l", "load", true, "where to get files list [multiple times is possible]"));
		action.setRequired(true);
		options.addOptionGroup(action);

		options.addOption("d", "dir", true, "a directory to scan [multiple times is possible]");
		options.addOption("f", "find", false, "find duplicates");
		options.addOption("y", "hierarchy", false, "aggregate over hierarchy");
	}

	public void initScan() {
		idGenerator = 0;
		allDirs = new TreeMap<>();
		fileToDirs = new HashMap<>();
		nbFilePerDir = new Counter<>();
	}

	public void scan(Path p, Consumer<Path> m) throws IOException {
		BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		if (attrs.isSymbolicLink()) {
			return;
		}
		if (attrs.isDirectory()) {
			try {
				for (Iterator<Path> i = Files.list(p).iterator(); i.hasNext();) {
					scan(i.next(), m);
				}
			} catch (AccessDeniedException e) {
				Log.log(true, e);
			}
		}
		if (attrs.isRegularFile()) {
			m.accept(p);
		}
	}

	public void setMaxNbDirForFile(int maxNbDirForFile) {
		this.maxNbDirForFile = maxNbDirForFile;
	}

	public void setMinNbCommonFiles(int minNbCommonFiles) {
		this.minNbCommonFiles = minNbCommonFiles;
	}

	public void start(String[] args) {
		initOptions();

		CommandLineParser parser = new DefaultParser();
		CommandLine command = null;
		try {
			command = parser.parse(options, args);
		} catch (ParseException e) {
			help(true, e.getMessage());
			return;
		}

		if (command.hasOption('h')) {
			help(false, getClass().getSimpleName());
			return;
		}

		if (command.hasOption('o')) {
			allFiles = new TreeSet<>();
			File output = new File(command.getOptionValue('o'));
			Log.log("Storing files listing in " + output);
			BufferedWriter w = null;
			try {
				w = new BufferedWriter(new FileWriter(output));

				for (String param : command.getOptionValues('d')) {
					Path path = Paths.get(param).toAbsolutePath();
					Log.log(" - listing files from " + path);
					scan(path, p -> allFiles.add(p.toString()));
				}

				for (String p : allFiles) {
					w.write(p + "\n");
				}
			} catch (IOException e) {
				Log.log(true, e);
			} finally {
				if (w != null) {
					try {
						w.close();
					} catch (IOException e) {
						// ignore
					}
				}
			}

			return;
		}

		if (command.hasOption('l')) {
			initScan();
			BufferedReader r = null;

			try {
				for (String param : command.getOptionValues('l')) {
					File input = new File(param);
					Log.log("Loading files listing from " + input);
					r = new BufferedReader(new FileReader(input));
					String line = null;
					while ((line = r.readLine()) != null) {
						line = line.strip();
						if (!line.isBlank() && !line.startsWith("#")) {
							Path path = Paths.get(line).toAbsolutePath();
							addFileToIndex(path);
						}
					}
					r.close();
				}
				hierarchyStats();
			} catch (IOException e) {
				Log.log(true, e);
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

		if (command.hasOption('s')) {
			initScan();
			try {
				for (String param : command.getOptionValues('d')) {
					Path path = Paths.get(param).toAbsolutePath();
					Log.log("Scanning files listing from " + path);
					scan(path, p -> addFileToIndex(p));
				}
				hierarchyStats();
			} catch (IOException e) {
				Log.log(true, e);
			}
		}

		if (command.hasOption('f')) {
			for (DuplicateDir dup : findDuplicates(command.hasOption('y'))) {
				Log.log("" + dup);
			}
			return;
		}
	}

}
