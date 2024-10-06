package com.ftc_dog.ftc_log;

import android.os.Environment;

import androidx.annotation.Keep;

import com.qualcomm.robotcore.util.RobotLog;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoWriter;
import org.openftc.easyopencv.OpenCvPipeline;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * Provide a pipeline step that will write the current video to a file in the FIRST directory.
 * This is only done if there is at least MINIMUM_FREE_SPACE available on the REV Robotics Control
 * Hub.  If there are existing video files, only that most recent KEEP_VIDEO_FILES are retained.
 *   Videos are always written in the MJPG codec, as that is always available on recent SDKs.
 *   The frame rate must be specified at the start of recording a video; if the pipeline is not
 * called often enough, the video will not actually be this frame rate, and will report a shorter
 * duration and playback faster than the actual event.  If the stream is closed (optional), a
 * message to the standard RobotLog is added at the end to report the approximate realized frame
 * rate.
 *   To use this, add it as a step in your OpenCvPipeline, like so
 * <PRE>
 *     // This would be your existing pipeline
 *     public class GameDetection extends OpenCvPipeline {
 *       public LogCameraStreamPipeline logPipelineRaw = new LogCameraStreamPipeline("raw");
 *       public LogCameraStreamPipeline logPipelineProcessed = new LogCameraStreamPipeline("processed");
 *       ...
 *       // Your pipeline's processFrame method
 *       public Mat processFrame(Mat input) {
 *         // record the input frames to your pipeline
 *         logPipelineRaw.processFrame(input);
 *         // all of your pipeline code to do processing is here, generating some Mat output result
 *         ...
 *         // record the processed frames to another video
 *         logPipelineProcessed.processFrame(output);
 *         return output;
 * </PRE>
 *   At the end of your opmode in the stop method (or, if the pipeline is only used during the
 * init_loop, in the start method), you should call the <pre>close</pre> method of the pipeline to
 * ensure that the video will be written out in a timely fashion.
 */
@Keep
public class LogCameraStreamPipeline extends OpenCvPipeline {
    String subName = "";
    VideoWriter videoWriter;
    Boolean initialized = false;

    String videoFilePath;
    long startTime = 0;
    long frameCount = 0;

    static String VIDEO_FILE_NAME = "video";
    static String VIDEO_FILE_EXTENSION = ".avi";
    static long MINIMUM_FREE_SPACE = 128 * 1024 * 1024;
    static int KEEP_VIDEO_FILES = 12;
    double FPS = 12.0;

    /**
     * Create a video logging pipeline with the default file name and frame rate.
     */
    public LogCameraStreamPipeline() {
    }

    /**
     * Create a video logging pipeline with the default frame rate.
     *
     * @param partialName A name to include as part of the video file name.
     */
    public LogCameraStreamPipeline(String partialName) {
        subName = partialName;
    }

    /**
     * Create a video logging pipeline.
     *
     * @param partialName A name to include as part of the video file name.
     * @param frameRate The frames-per-second for the video.  If the video cannot be recorded this
     *                  quickly, the resulting video will appear to be short and play too fast.
     */
    public LogCameraStreamPipeline(String partialName, double frameRate) {
        subName = partialName;
        FPS = frameRate;
    }

    /**
     * Process a frame.  If recording and the frame rate requires it, write the frame to the video
     * file.
     *
     * @param frame An opencv Mat containing the video frame.
     * @return The unchanged frame.
     */
    public Mat processFrame(Mat frame) {
        if (!initialized) {
            String videoDirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/FIRST";
            //noinspection SpellCheckingInspection
            File noMediaFile = new File(videoDirPath, ".nomedia");
            try {
                if (!noMediaFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    noMediaFile.createNewFile();
                }
            } catch (IOException ignored) {

            }
            videoFilePath = videoDirPath + "/" + VIDEO_FILE_NAME + (subName.isEmpty() ? "" : "_") + subName + new SimpleDateFormat("_yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + VIDEO_FILE_EXTENSION;
            if (new File(Environment.getExternalStorageDirectory().getAbsolutePath()).getFreeSpace() > MINIMUM_FREE_SPACE) {
                rotateVideoFile(videoDirPath);
            }

            Size frameSize = new Size(frame.size().width, frame.size().height);
            videoWriter = new VideoWriter(videoFilePath, VideoWriter.fourcc('M', 'J', 'P', 'G'), FPS, frameSize, true);
            initialized = true;
            startTime = System.currentTimeMillis();
            frameCount = 0;
            RobotLog.i(String.format(Locale.getDefault(), "LOG_CAMERA_STREAM %s Started %s %3.1f FPS", subName, videoFilePath, FPS));
        }
        if (videoWriter != null && videoWriter.isOpened()) {
            // keep track of the time and skip frames to maintain frame rate
            RobotLog.i(String.format(Locale.getDefault(), "LOG_CAMERA_STREAM %s Frames: %d Duration: %dms", subName, frameCount, System.currentTimeMillis() - startTime));
            if (frameCount == 0 || frameCount < (System.currentTimeMillis() - startTime) * FPS / 1000) {
                videoWriter.write(frame);
                frameCount += 1;
            }
            if (frameCount + 2 < (System.currentTimeMillis() - startTime) * FPS / 1000) {
                RobotLog.i("LOG_CAMERA_STREAM Frame rate higher than write speed");
            }
        }
        return frame;
    }

    /**
     * This checks for existing video files, deleting the oldest so that we don't fill up the
     * available storage space.
     *
     * @param videoDirPath The local path to the video files.
     */
    static private void rotateVideoFile(String videoDirPath) {
        try {
            File videoDir = new File(videoDirPath);
            if (videoDir.isDirectory()) {
                File[] files = videoDir.listFiles((dir, name) ->
                        name.startsWith(VIDEO_FILE_NAME) && name.endsWith(VIDEO_FILE_EXTENSION));
                if (files != null && files.length >= KEEP_VIDEO_FILES) {
                    Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                    for (int i = 0; i <= files.length - KEEP_VIDEO_FILES; i++) {
                        //noinspection ResultOfMethodCallIgnored
                        files[i].delete();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Indicate that we are done writing the video.  We actually let the garbage collection process
     * trigger writing the file to storage since otherwise it can take long enough that the program
     * will be terminated and we will not have the final video.
     */
    public void close() {
        if (videoWriter != null) {
            // Let GC clean up the stream; otherwise the opmode may terminate the process.  This
            // does not do "videoWriter.release();".
            videoWriter = null;
            RobotLog.i(String.format(Locale.getDefault(), "LOG_CAMERA_STREAM %s Ended Frames: %d Duration: %dms (%5.3f FPS)",
                    subName, frameCount, System.currentTimeMillis() - startTime, (double)(frameCount - 1) * 1000 / (System.currentTimeMillis() - startTime)));
        }
    }

}
