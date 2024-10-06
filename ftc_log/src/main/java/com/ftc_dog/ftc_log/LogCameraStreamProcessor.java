package com.ftc_dog.ftc_log;


import android.graphics.Canvas;

import androidx.annotation.Keep;

import org.firstinspires.ftc.robotcore.internal.camera.calibration.CameraCalibration;
import org.firstinspires.ftc.vision.VisionProcessor;
import org.opencv.core.Mat;

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
 *   To use this, add it as a step in your VisionProcessor, like so
 * <PRE>
 *     VisionPortal.Builder builder = new VisionPortal.Builder();
 *
 *     logPipelineRaw = new LogCameraStreamProcessor("raw");
 *     logPipelineProcessed = new LogCameraStreamProcessor("processed");
 *
 *     builder.setCamera(webcam)
 *            .addProcessor(logPipelineRaw)
 *            .addProcessor(myProcessingPipeline)
 *            .addProcessor(logPipelineProcessed);
 *     visionPortal = builder.build();
 * </PRE>
 *   At the end of your opmode in the stop method (or, if the pipeline is only used during the
 * init_loop, in the start method), you should call the <pre>close</pre> method of the pipeline to
 * ensure that the video will be written out in a timely fashion.
 */
@Keep
public class LogCameraStreamProcessor implements VisionProcessor {
    private final LogCameraStreamPipeline pipeline;

    /**
     * Create a video logging processor with the default file name and frame rate.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public LogCameraStreamProcessor() {
        pipeline = new LogCameraStreamPipeline();
    }

    /**
     * Create a video logging processor with the default frame rate.
     *
     * @param partialName A name to include as part of the video file name.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public LogCameraStreamProcessor(String partialName) {
        pipeline = new LogCameraStreamPipeline(partialName);
    }

    /**
     * Create a video logging processor.
     *
     * @param partialName A name to include as part of the video file name.
     * @param frameRate The frames-per-second for the video.  If the video cannot be recorded this
     *                  quickly, the resulting video will appear to be short and play too fast.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public LogCameraStreamProcessor(String partialName, double frameRate) {
        pipeline = new LogCameraStreamPipeline(partialName, frameRate);
    }

    @Override
    public void init(int width, int height, CameraCalibration calibration) {
    }

    @Override
    public Mat processFrame(Mat frame, long captureTimeNanos) {
        return pipeline.processFrame(frame);
    }

    @Override
    public void onDrawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight,
                            float scaleBmpPxToCanvasPx, float scaleCanvasDensity,
                            Object userContext) {

    }

    /**
     * Indicate that we are done writing the video.  We actually let the garbage collection process
     * trigger writing the file to storage since otherwise it can take long enough that the program
     * will be terminated and we will not have the final video.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void close() {
        pipeline.close();
    }
}
