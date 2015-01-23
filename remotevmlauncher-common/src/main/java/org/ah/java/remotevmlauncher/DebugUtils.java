package org.ah.java.remotevmlauncher;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DebugUtils {

    public static void debug(Logger logger, ByteBuffer buffer) {
        if (logger.isLoggable(Level.FINEST)) {
            debug(logger, Level.FINEST, buffer);
        }
    }

    public static void debug(Logger logger, Level level, ByteBuffer buffer) {
        if (logger.isLoggable(level)) {
            StringBuilder sb = new StringBuilder();
            sb.append('\n');
            buffer.mark();
            int address = 0;
            int rowCount = 0;
            StringBuilder str = new StringBuilder();

            while (buffer.remaining() > 0) {

                if (rowCount == 0) {
                    sb.append(toHex(address, 8)).append(' ');
                } else if (rowCount == 8) {
                    sb.append(' ');
                }
                byte b = buffer.get();
                sb.append(' ');
                sb.append(toHex(b, 2));
                addChar(str, (char)b);
                rowCount++;
                address++;
                if (rowCount == 16) {
                    sb.append(" |").append(str).append("|\n");
                    str = new StringBuilder();
                    rowCount = 0;
                }
            }
            if (rowCount == 0) {
                // Do nothing
            } else {
                if (rowCount < 8) {
                    sb.append(' ');
                }
                sb.append("                                                 ".substring(0, (16 - rowCount) * 3));
                sb.append(" |").append(str).append("|\n");
            }

            buffer.reset();
            logger.log(level, sb.toString());
        }
    }

    protected static void addChar(StringBuilder sb, char c) {
        if (c < 32 || c > 127 || c < 0) {
            sb.append('.');
        } else {
            sb.append(c);
        }
    }

    protected static String toHex(int n, int s) {
        String str = "0000000000000000" + Integer.toHexString(n);
        return str.substring(str.length() - s);
    }
}
