package org.ah.java.remotevmlauncher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

public class JavaLoggingUtils {

    public static final String TIME_FORMATTER = "HH:mm:ss.SSS";
    
    public static void setupSimpleConsoleLogging(final int level) {
        Logger root = Logger.getLogger("");
        Formatter formatter = new Formatter() {
            public synchronized String format(LogRecord record) {
                String message;
                if (level > 1) {
                    message = new SimpleDateFormat(TIME_FORMATTER).format(new Date(record.getMillis())) + " " + record.getLoggerName() + " " + record.getMessage() + "\n";
                } else if (record.getLoggerName().length() > 0) {
                    message = record.getLoggerName() + " " + record.getMessage() + "\n";
                } else {
                    message = record.getMessage() + "\n";
                }
                if (record.getThrown() != null) {
                    StringWriter res = new StringWriter();
                    PrintWriter p = new PrintWriter(res);
                    record.getThrown().printStackTrace(p);
                    message = message + res.toString();
                }
                return message;
            }
        };

        Handler consoleHandler = new StreamHandler(System.out, formatter) {
            public void publish(LogRecord record) {
                super.publish(record);  
                flush();
            }

            public void close() {
                flush();
            }
        };
        consoleHandler.setLevel(Level.ALL);

        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        root.addHandler(consoleHandler);
        root.setLevel(Level.ALL);

        Logger logger = Logger.getLogger("");
        if (level <= 0) {
            logger.setLevel(Level.SEVERE);
        } else if (level == 1) {
            logger.setLevel(Level.INFO);
        } else if (level == 2) {
            logger.setLevel(Level.FINE);
        } else if (level == 3) {
            logger.setLevel(Level.FINER);
        } else if (level >= 4) {
            logger.setLevel(Level.FINEST);
        }
    }

}
