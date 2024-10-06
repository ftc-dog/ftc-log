package com.ftc_dog.ftc_log;

import android.os.Environment;

import androidx.annotation.Keep;

import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.robotcore.external.Func;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

/**
 * The TelemetryLog is a wrapper around the normal Telemetry instance that an opmode is running.
 * It can additionally send all telemetry messages to the RobotLog and / or to a file in the FIRST
 * folder on the Rev Robotics Control Hub.  The RobotLog is the log shown in the Logcat panel on
 * AndroidStudio and is also in the opmode logs that are stored in the FIRST/matchlogs folder.
 *   When storing logs to a file, they are stored in FIRST/telemetry_log.txt.  If this file is over
 * a specified size (ROTATE_LOG_SIZE) when the log is started, it is rotated to a file that
 * includes the current date and time and a new file is started.  A maximum of KEEP_LOG_FILES will
 * be kept.  Also, note that if the Control Hub doesn't have enough free space
 * (MINIMUM_FREE_SPACE), the log file will not be written.
 *   When storing to a file, at the end of your opMode in the stop method, you should call
 * <pre>telemetry.addLine("TelemetryLog:close");</pre> to specifically flush all of the logs to
 * disk.  Otherwise, this may not happen for quite some time.
 */
@Keep
public class TelemetryLog implements Telemetry {

    static Telemetry telemetry;
    static ArrayList<String> recentLogs = new ArrayList<>();
    static FileWriter fileWriter;
    static boolean toRobotLog = true;
    static String logFilePath;
    static long startTime = 0;

    static int MAX_RECENT_LIST = 50;
    static String LOG_FILE_NAME = "telemetry_log";
    static String LOG_FILE_EXTENSION = ".txt";
    static long MINIMUM_FREE_SPACE = 128 * 1024 * 1024;
    static long ROTATE_LOG_SIZE = 2 * 1024 * 1024;
    static int KEEP_LOG_FILES = 4;

    /**
     * Wrap the telemetry object to log to one or both of the RobotLog and to a rotating log file.
     *
     * @param telemetry The telemetry object to wrap.  If it has already been wrapped, it is not
     *                  wrapped a second time.
     * @param toLog If True, log to the RobotLog.  this is visible in the application log and in
     *              the opmode logs, but sometimes rotates away and also contains many other log
     *              messages.
     * @param toFile If True, log to the telemetry_log.txt file.  This will only occur if the
     *               FIRST directory is writable and has a minimum amount of free space.  If the
     *               log file exists and is over a particular size (see constants), then the log
     *               file will be rotated, with older log files marked with the date and time of
     *               rotation.  The date and time may not be accurate, depending on how the
     *               robot controller is initialized.
     * @return A wrapped telemetry object.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    static public Telemetry logTelemetry(Telemetry telemetry, Boolean toLog, Boolean toFile) {
        toRobotLog = toLog;
        startTime = System.currentTimeMillis();

        closeFileWriter();
        recentLogs = new ArrayList<>();
        if (toFile) {
            try {
                String logDirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/FIRST";
                logFilePath = logDirPath + "/" + LOG_FILE_NAME + LOG_FILE_EXTENSION;
                if (new File(Environment.getExternalStorageDirectory().getAbsolutePath()).getFreeSpace() > MINIMUM_FREE_SPACE) {
                    if (new File(logFilePath).exists() && new File(logFilePath).length() >= ROTATE_LOG_SIZE) {
                        rotateLogFile(logDirPath, logFilePath);
                    }
                    fileWriter = new FileWriter(logFilePath, true);
                }
            } catch (IOException e) {
                fileWriter = null;
            }
        }
        if (!(telemetry instanceof TelemetryLog)) {
            TelemetryLog.telemetry = telemetry;
            telemetry = new TelemetryLog();

            if (toRobotLog) {
                RobotLog.i("Starting TelemetryLog");
            }
            if (fileWriter != null) {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
                df.setTimeZone(TimeZone.getTimeZone("UTC"));
                String dateStr = df.format(new Date());
                try {
                    fileWriter.write(dateStr + " Starting TelemetryLog\n");
                    fileWriter.flush();
                } catch (IOException e) {
                    closeFileWriter();
                }
            }
        } else {
            if (toRobotLog) {
                RobotLog.i("Continuing TelemetryLog");
            }
            if (fileWriter != null) {
                try {
                    fileWriter.write(String.format(Locale.getDefault(), "%5.3fs Continuing TelemetryLog\n", (System.currentTimeMillis() - startTime) * 0.001));
                    fileWriter.flush();
                } catch (IOException e) {
                    closeFileWriter();
                }
            }
        }

        return telemetry;
    }

    /**
     * Check if the log file exists and is larger than a defined size.  If so, rotate the log file,
     * keeping only so many of such files.
     *
     * @param logDirPath The path of the log files.
     * @param logFilePath The active log file.
     */
    static private void rotateLogFile(String logDirPath, String logFilePath) {
        try {
            String newFilePath = logDirPath + "/" + LOG_FILE_NAME + new SimpleDateFormat("_yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + LOG_FILE_EXTENSION;
            File oldLogFile = new File(logFilePath);
            File newLogFile = new File(newFilePath);
            if (oldLogFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                oldLogFile.renameTo(newLogFile);
            }

            File logDir = new File(logDirPath);
            if (logDir.isDirectory()) {
                File[] files = logDir.listFiles((dir, name) ->
                        name.startsWith(LOG_FILE_NAME) && name.endsWith(LOG_FILE_EXTENSION));
                if (files != null && files.length >= KEEP_LOG_FILES) {
                    Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                    for (int i = 0; i <= files.length - KEEP_LOG_FILES; i++) {
                        //noinspection ResultOfMethodCallIgnored
                        files[i].delete();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Close the file writer.  This should be called on errors or when done.
     */
    static private void closeFileWriter() {
        if (fileWriter != null) {
            try {
                fileWriter.close();
            } catch (IOException ignored) {
            }
            fileWriter = null;
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    static public Telemetry logTelemetry(Telemetry telemetry) {
        return logTelemetry(telemetry, true, true);
    }

    /**
     * Log a string to the RobotLog and/or the log file.
     *
     * @param value A string to log.
     */
    public void logString(String value) {
        // if we logged the value recently, move it to the most recent spot in the list and don't
        // log it again.
        if (recentLogs.contains(value)) {
            recentLogs.remove(value);
            recentLogs.add(value);
            return;
        }
        // if we logged something with the same caption recently, remove the older log with that
        // caption, so if we bounce between values we log the back-and-forth
        if (value.contains(":")) {
            String caption = value.split(":")[0];
            for (int i = 0; i < recentLogs.size(); i += 1) {
                if (recentLogs.get(i).contains(":") && recentLogs.get(i).split(":")[0].equals(caption)) {
                    recentLogs.remove(i);
                    break;
                }
            }
        }
        // don't let the log grow too big
        if (recentLogs.size() >= MAX_RECENT_LIST) {
            recentLogs.remove(0);
        }
        recentLogs.add(value);
        if (toRobotLog) {
            RobotLog.i(value);
        }
        if (fileWriter != null) {
            try {
                fileWriter.write(String.format(Locale.getDefault(), "%5.3fs ", (System.currentTimeMillis() - startTime) * 0.001));
                fileWriter.write(value);
                fileWriter.write("\n");
                fileWriter.flush();
            } catch (IOException e) {
                closeFileWriter();
            }
        }
    }

    @Override
    public Item addData(String caption, String format, Object... args) {
        logString(caption + ": " + String.format(format, args));
        if (telemetry != null) {
            return telemetry.addData(caption, format, args);
        }
        return null;
    }

    @Override
    public Item addData(String caption, Object value) {
        logString(caption + ": " + value.toString());
        if (telemetry != null) {
            return telemetry.addData(caption, value);
        }
        return null;
    }

    @Override
    public <T> Item addData(String caption, Func<T> valueProducer) {
        logString(caption + ": " + valueProducer.value().toString());
        if (telemetry != null) {
            return telemetry.addData(caption, valueProducer);
        }
        return null;
    }

    @Override
    public <T> Item addData(String caption, String format, Func<T> valueProducer) {
        logString(caption + ": " + String.format(format, valueProducer.value().toString()));
        if (telemetry != null) {
            return telemetry.addData(caption, format, valueProducer);
        }
        return null;
    }

    @Override
    public boolean removeItem(Item item) {
        if (telemetry != null) {
            return telemetry.removeItem(item);
        }
        return false;
    }

    @Override
    public void clear() {
        if (telemetry != null) {
            telemetry.clear();
        }
    }

    @Override
    public void clearAll() {
        if (telemetry != null) {
            telemetry.clearAll();
        }
    }

    @Override
    public Object addAction(Runnable action) {
        if (telemetry != null) {
            return telemetry.addAction(action);
        }
        return null;
    }

    @Override
    public boolean removeAction(Object token) {
        if (telemetry != null) {
            return telemetry.removeAction(token);
        }
        return false;
    }

    @Override
    public void speak(String text) {
        if (telemetry != null) {
            telemetry.speak(text);
        }
    }

    @Override
    public void speak(String text, String languageCode, String countryCode) {
        if (telemetry != null) {
            telemetry.speak(text, languageCode, countryCode);
        }
    }

    @Override
    public boolean update() {
        if (telemetry != null) {
            return telemetry.update();
        }
        return false;
    }

    @Override
    public Line addLine() {
        if (telemetry != null) {
            return telemetry.addLine();
        }
        return null;
    }

    /**
     * This invokes the telemetry add line and logs the string.  Some special values can be passed
     * to control the log wrapped.  These are
     * TelemetryLog:close - close writing to the log file.
     *
     * @param lineCaption the caption for the line
     * @return Either null or a Line object.
     */
    @Override
    public Line addLine(String lineCaption) {
        if (Objects.equals(lineCaption, "TelemetryLog:close")) {
            closeFileWriter();
            return null;
        }
        logString(lineCaption);
        if (telemetry != null) {
            return telemetry.addLine(lineCaption);
        }
        return null;
    }

    @Override
    public boolean removeLine(Line line) {
        if (telemetry != null) {
            return telemetry.removeLine(line);
        }
        return false;
    }

    @Override
    public boolean isAutoClear() {
        if (telemetry != null) {
            return telemetry.isAutoClear();
        }
        return false;
    }

    @Override
    public void setAutoClear(boolean autoClear) {
        if (telemetry != null) {
            telemetry.setAutoClear(autoClear);
        }
    }

    @Override
    public int getMsTransmissionInterval() {
        if (telemetry != null) {
            return telemetry.getMsTransmissionInterval();
        }
        return 0;
    }

    @Override
    public void setMsTransmissionInterval(int msTransmissionInterval) {
        if (telemetry != null) {
            telemetry.setMsTransmissionInterval(msTransmissionInterval);
        }
    }

    @Override
    public String getItemSeparator() {
        if (telemetry != null) {
            return telemetry.getItemSeparator();
        }
        return null;
    }

    @Override
    public void setItemSeparator(String itemSeparator) {
        if (telemetry != null) {
            telemetry.setItemSeparator(itemSeparator);
        }
    }

    @Override
    public String getCaptionValueSeparator() {
        if (telemetry != null) {
            return telemetry.getCaptionValueSeparator();
        }
        return null;
    }

    @Override
    public void setCaptionValueSeparator(String captionValueSeparator) {
        if (telemetry != null) {
            telemetry.setCaptionValueSeparator(captionValueSeparator);
        }
    }

    @Override
    public void setDisplayFormat(DisplayFormat displayFormat) {
        if (telemetry != null) {
            telemetry.setDisplayFormat(displayFormat);
        }
    }

    @Override
    public Log log() {
        if (telemetry != null) {
            return telemetry.log();
        }
        return null;
    }
}
