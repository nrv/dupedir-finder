package name.herve.dupedir;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {
	private static SimpleDateFormat DTF;

	static {
		DTF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	}

	public static void log(boolean isError, String msg) {
		@SuppressWarnings("resource")
		PrintStream ps = isError ? System.err : System.out;
		ps.println("[" + DTF.format(new Date()) + "]   " + msg);
	}

	public static void log(boolean isError, Throwable e) {
		log(isError, e.getClass().getName() + " : " + e.getMessage());
	}

	public static void log(String msg) {
		log(false, msg);
	}
}
