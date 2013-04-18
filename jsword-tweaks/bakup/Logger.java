/**
 * Distribution License:
 * JSword is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License, version 2.1 as published by
 * the Free Software Foundation. This program is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The License is available on the internet at:
 *       http://www.gnu.org/copyleft/lgpl.html
 * or by writing to:
 *      Free Software Foundation, Inc.
 *      59 Temple Place - Suite 330
 *      Boston, MA 02111-1307, USA
 *
 * Copyright: 2005
 *     The copyright to this program is held by it's authors.
 *
 * ID: $Id: Logger.java 2050 2010-12-09 15:31:45Z dmsmith $
 */
package org.crosswire.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.MissingResourceException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * This class is very similar to Commons-Logging except it should be even
 * smaller and have an API closer to the Log4J API (and even J2SE 1.4 logging).
 * 
 * This implementation is lazy. The actual internal logger is not initialized
 * until first use. Turns out that this class indirectly depends upon JSword's
 * Project class to help find the logging configuration file. If it is not lazy,
 * it looks in the wrong places for the configuration file.
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 * @author DM Smith [dmsmith555 at yahoo dot com]
 */
public final class Logger {
    /**
     * Get a new logger for the class that shows the class, method and line
     * number of the caller.
     * @param clazz the class that holds the logger.
     */
    public static <T> Logger getLogger(Class<T> clazz) {
        return getLogger(clazz, true);
    }

    /**
     * Get a new logger for the class that shows the class of the caller.
     * @param clazz the class that holds the logger.
     * @param showLocation when true it will get the method and line where logging occurred.
     */
    public static <T> Logger getLogger(Class<T> clazz, boolean showLocation) {
        return new Logger(clazz, showLocation);
    }

    /**
     * Set the level at which output occurs for this Logger.
     * 
     * @param newLevel
     *            the level to apply
     */
    public void setLevel(Level newLevel) {
        logger.setLevel(newLevel);
    }

    /**
     * Stop all logging output
     */
    public static synchronized void outputNothing() {
        level = Level.OFF;
    }

    /**
     * Output a minimum of stuff
     */
    public static synchronized void outputInfoMinimum() {
        level = Level.WARNING;
    }

    /**
     * Output everything
     */
    public static synchronized void outputEverything() {
        level = Level.ALL;
    }

    /**
     * Log a message object with the SEVERE level.
     * 
     * @param msg
     *            the message to log.
     */
    public void fatal(String msg) {
        doLogging(Level.SEVERE, msg, null);
    }

    /**
     * Log a message object with the SEVERE level.
     * 
     * @param msg
     *            the message object to log.
     */
    public void fatal(String msg, Throwable th) {
        doLogging(Level.SEVERE, msg, th);
    }

    /**
     * Log a message object with the WARNING level.
     * 
     * @param msg
     *            the message to log.
     */
    public void error(String msg) {
        doLogging(Level.WARNING, msg, null);
    }

    /**
     * Log a message object with the WARNING level.
     * 
     * @param msg
     *            the message to log.
     * @param th
     *            the exception to note when not null
     */
    public void error(String msg, Throwable th) {
        doLogging(Level.WARNING, msg, th);
    }

    /**
     * Log a message object with the INFO level.
     * 
     * @param msg
     *            the message object to log.
     */
    public void info(String msg) {
        doLogging(Level.INFO, msg, null);
    }

    /**
     * Log a message object with the INFO level.
     * 
     * @param msg
     *            the message object to log.
     * @param th
     *            the exception to note when not null
     */
    public void info(String msg, Throwable th) {
        doLogging(Level.INFO, msg, th);
    }

    /**
     * Log a message object with the FINE level.
     * 
     * @param msg
     *            the message object to log.
     */
    public void warn(String msg) {
        doLogging(Level.FINE, msg, null);
    }

    /**
     * Log a message object with the FINE level.
     * 
     * @param msg
     *            the message object to log.
     * @param th
     *            the exception to note when not null
     */
    public void warn(String msg, Throwable th) {
        doLogging(Level.FINE, msg, th);
    }

    /**
     * Log a message object with the FINEST level.
     * 
     * @param msg
     *            the message object to log.
     */
    public void debug(String msg) {
        doLogging(Level.FINEST, msg, null);
    }

    /**
     * Log a message with the supplied level.
     * 
     * @param lev
     *            the level at which to log.
     * @param msg
     *            the message to log.
     */
    public void log(Level lev, String msg) {
        doLogging(lev, msg, null);
    }

    /**
     * Log a message with the supplied level, recording the exception when not
     * null.
     * 
     * @param msg
     *            the message object to log.
     */
    public void log(Level lev, String msg, Throwable th) {
        doLogging(lev, msg, th);
    }

    /**
     * Create a logger for the class. Wrapped by {@link #java.util.logging.Logger.getLogger(String)}.
     */
    private <T> Logger(Class<T> id, boolean showLocation) {
        this.logger = java.util.logging.Logger.getLogger(id.getName());
        this.showLocation = showLocation;
    }

    // Private method to infer the caller's class and method names
    private void doLogging(Level theLevel, String message, Throwable th) {
        initialize();

        LogRecord logRecord = new LogRecord(theLevel, message);
        logRecord.setLoggerName(logger.getName());
        logRecord.setSourceClassName(CallContext.getCallingClass(1).getName());
        logRecord.setThrown(th);

        if (showLocation) {
            String methodName = null;
            int lineNumber = -1;

            // Get the stack trace.
            StackTraceElement[] stack = (new Throwable()).getStackTrace();

            // First, search back to a method in the Logger class.
            int ix = 0;
            while (ix < stack.length) {
                StackTraceElement frame = stack[ix];
                String cname = frame.getClassName();
                if (cname.equals(CLASS_NAME)) {
                    break;
                }
                ix++;
            }

            // Now search for the first frame with the name of the caller.
            while (ix < stack.length) {
                StackTraceElement frame = stack[ix];
                if (!frame.getClassName().equals(CLASS_NAME)) {
                    // We've found the relevant frame.
                    methodName = frame.getMethodName();
                    lineNumber = frame.getLineNumber();
                    break;
                }
                ix++;
            }

            logRecord.setSourceMethodName(methodName);
            // This is a non-standard use of sequence number.
            // We could just subclass LogRecord and add line number.
            logRecord.setSequenceNumber(lineNumber);
        }

        //MJD this line added because I could not get this logger to work on Android
        System.out.println("JSword:"+message);
        
        logger.log(logRecord);
    }

    private synchronized void initialize() {
        Logger.establishLogging();
        Logger.setLevel();
    }

    private static void establishLogging() {
        if (established) {
            return;
        }
        established = true;

        Exception ex = null;
        try {
            InputStream cwConfigStream = ResourceUtil.getResourceAsStream("CWLogging.properties");
            LogManager.getLogManager().readConfiguration(cwConfigStream);
        } catch (SecurityException e) {
            ex = e;
        } catch (MissingResourceException e) {
            ex = e;
        } catch (IOException e) {
            ex = e;
        }
        if (ex != null) {
            cwLogger.info("Can't load CWLogging.properties", ex);
        }
    }

    private static void setLevel() {
        // If there was a request to change the minimum level of logging
        // handle it now.
        if (Logger.level != null) {
            // There are two parts of making a log message get out.
            // It has to be more important than the level:
            //     a) of the logger
            //     b) of the handler
            // So we need to set both.
            // In the case of the handlers, we set all of them.
            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger(ROOT_LOGGER);
            Handler[] handlers = rootLogger.getHandlers();
            for (int index = 0; index < handlers.length; index++) {
                handlers[index].setLevel(Level.FINE);
            }
            rootLogger.setLevel(Logger.level);
            // Don't do this again unless asked.
            Logger.level = null;
        }
    }

    private static final String ROOT_LOGGER = "";
    private static final String CLASS_NAME = Logger.class.getName();
    private static volatile boolean established;
    private static volatile Level level;

    /**
     * The actual logger.
     */
    private java.util.logging.Logger logger;
    private static Logger cwLogger = getLogger(Logger.class);

    /**
     * Whether we dig into the call stack to get the method and line number of
     * the caller.
     */
    private boolean showLocation;
}
