package org.janelia.saalfeldlab.n5.bdv.movie;

import bdv.tools.movie.preview.MovieFrameInst;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VideoProducerStep implements Serializable {
    private final int frame;
    private final int step;
    private final int fileIndex;

    public VideoProducerStep(int frame, int step, int fileIndex) {
        this.frame = frame;
        this.step = step;
        this.fileIndex = fileIndex;
    }

    public int getFrame() {
        return frame;
    }

    public int getStep() {
        return step;
    }

    public int getFileIndex() {
        return fileIndex;
    }

    public static List<ArrayList<VideoProducerStep>> generateBatches(List<MovieFrameInst> movieFrames, int batch) {
        List<ArrayList<VideoProducerStep>> result = new ArrayList<>();
        int batchStep = 0;
        ArrayList<VideoProducerStep> currentStep = new ArrayList<>();
        int fileIndex = 0;
        for (int frame = 1; frame < movieFrames.size(); ++frame)
            for (int step = 0; step < movieFrames.get(frame).getFrames(); ++step) {
                if (batchStep >= batch) {
                    result.add(currentStep);
                    currentStep = new ArrayList<>();
                    batchStep = 0;
                }
                currentStep.add(new VideoProducerStep(frame, step, fileIndex++));
                batchStep++;
            }
        if (!currentStep.isEmpty())
            result.add(currentStep);
        return result;
    }
}
