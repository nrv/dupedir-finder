package name.herve.dupedir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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

	private static DecimalFormat DECF = new DecimalFormat("###,###");

	private class Dir implements Comparable<Dir> {
		private Path path;
		private int nbFiles;

		public Dir(Path path) {
			super();
			this.path = path;
			this.nbFiles = -1;
		}

		@Override
		public int compareTo(Dir o) {
			return path.compareTo(o.path);
		}

		@Override
		public int hashCode() {
			return path.hashCode();
		}

		@Override
		public String toString() {
			return "[" + nbFiles + "] " + path.toString();
		}

		public int getNbFiles() {
			return nbFiles;
		}

		public void setNbFiles(int nbFiles) {
			this.nbFiles = nbFiles;
		}

		public Path getPath() {
			return path;
		}
	}

	private class DuplicateDir {
		private Dir dir1;
		private Dir dir2;
		private int nbCommonFiles;
	}

	public static void main(String[] args) {
		new DupedirFinder().start(args);
	}

	private Options options;
	private Map<String, Set<Dir>> fileToDir;
	private Set<String> allPath;
	private Counter<String> nbFilePerDir;

	public DupedirFinder() {
		super();
	}

	public void scan(Path p, Consumer<Path> m) throws IOException {
		BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		if (attrs.isSymbolicLink()) {
			return;
		}
		if (attrs.isDirectory()) {
			for (Iterator<Path> i = Files.list(p).iterator(); i.hasNext();) {
				scan(i.next(), m);
			}
		}
		if (attrs.isRegularFile()) {
			m.accept(p);
		}
	}

	public void addToIndex(Path file) {
		String name = file.getFileName().toString();
		Set<Dir> dirs = fileToDir.get(name);
		if (dirs == null) {
			dirs = new TreeSet<>();
			fileToDir.put(name, dirs);
		}
		Path dir = file.getParent();
		if (dirs.add(new Dir(dir))) {
			nbFilePerDir.add(dir.toString());
		}
	}

	public void findDuplicates() {
		for (Entry<String, Set<Dir>> e : fileToDir.entrySet()) {
			if (e.getValue().size() > 1) {
				Log.log("[" + e.getValue().size() + "] " + e.getKey());
			}
		}
	}

	private void initOptions() {
		options = new Options();
		options.addOption("h", "help", false, "print this help");

		OptionGroup action = new OptionGroup();
		action.addOption(new Option("s", "scan", false, "launch scan"));
		action.addOption(new Option("o", "list", true, "where to store files list"));
		action.addOption(new Option("l", "load", true, "where to get files list"));
		action.setRequired(true);
		options.addOptionGroup(action);

		options.addOption("d", "dir", true, "a directory to scan");
		options.addOption("f", "find", false, "find duplicates");
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
			allPath = new TreeSet<>();
			File output = new File(command.getOptionValue('o'));
			BufferedWriter w = null;
			try {
				w = new BufferedWriter(new FileWriter(output));

				for (String param : command.getOptionValues('d')) {
					Path path = Paths.get(param).toAbsolutePath();
					scan(path, p -> allPath.add(p.toString()));
				}
				
				for (String p : allPath) {
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

		if (command.hasOption('s')) {
			fileToDir = new HashMap<>();
			nbFilePerDir = new Counter<>();
			try {
				for (String param : command.getOptionValues('d')) {
					Path path = Paths.get(param).toAbsolutePath();
					scan(path, p -> addToIndex(p));
				}
			} catch (IOException e) {
				Log.log(true, e);
			}
		}

		if (command.hasOption('f')) {
			findDuplicates();
			return;
		}
	}

}
