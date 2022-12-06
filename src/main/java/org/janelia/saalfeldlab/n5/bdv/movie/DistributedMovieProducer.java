package org.janelia.saalfeldlab.n5.bdv.movie;

import bdv.cache.CacheControl;
import bdv.tools.movie.MovieProducer;
import bdv.tools.movie.preview.MovieFrameInst;
import bdv.tools.movie.serilizers.AffineTransform3DJsonSerializer;
import bdv.tools.movie.serilizers.MovieFramesSerializer;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerState;
import bdv.viewer.animate.SimilarityTransformAnimator;
import bdv.viewer.overlay.MultiBoxOverlayRenderer;
import bdv.viewer.overlay.ScaleBarOverlayRenderer;
import bdv.viewer.render.MultiResolutionRenderer;
import bdv.viewer.render.PainterThread;
import net.imglib2.realtransform.AffineTransform3D;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import picocli.CommandLine;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import picocli.CommandLine.Option;

import static bdv.tools.movie.MovieProducer.accel;

public class DistributedMovieProducer implements Callable<Void>, Serializable {

    @Option(names = {"-i", "--n5Path"}, required = false, description = "N5 path for saving, e.g. /home/fused.n5")
    private String n5Path = "/Volumes/flyem/render/n5/Z0720_07m_CNS.n5";

    @Option(names = {"-d", "--n5Dataset"}, required = false, description = "N5 dataset - e.g. /ch488")
    private String n5Group = "/full_cns";

    @Option(names = {"-j", "--json"}, required = false, description = "json file generated by BDV movie producer, e.g. /home/transformations.json")
    private String jsonTransformations = "/Users/zouinkhim/Desktop/test.json";


    @Option(names = {"-o", "--out"}, required = false, description = "Output folder for PNGs")
    private String outputFolder = "/Users/zouinkhim/Desktop/test_video";


    @Option(names = {"-w", "--width"}, required = false, description = "Output Width")
    int width = 8000;

    @Option(names = {"-h", "--height"}, required = false, description = "Output Height")
    int height = 8000;


    @Override
    public Void call() throws Exception {
        final long time = System.currentTimeMillis();

        final String n5Path = this.n5Path;
        final String n5Group = this.n5Group;
        final String outputFolder = this.outputFolder;
        final Integer width = this.width;
        final Integer height = this.height;

        List<MovieFrameInst> movieFrames = MovieFramesSerializer.getFrom(new File(jsonTransformations));

        int size = movieFrames.size();


        final List<double[]> transforms = new ArrayList<>();
        final List<Integer> frames = new ArrayList<>();
        final List<Integer> accel = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            MovieFrameInst currentFrame = movieFrames.get(i);
            transforms.add(currentFrame.getTransform().getRowPackedCopy());
            frames.add(currentFrame.getFrames());
            accel.add(currentFrame.getAccel());
        }

        final SparkConf conf = new SparkConf().setAppName("DistributedMovieProducer");
        conf.setMaster("local");
        // TODO: REMOVE
        conf.set("spark.driver.bindAddress", "127.0.0.1");

        final JavaSparkContext sc = new JavaSparkContext(conf);
        sc.setLogLevel("ERROR");
        List<int[]> elements = new ArrayList<>();
        int i = 0;
        for (int k = 1; k < size; ++k) {
            for (int n = 0; n < frames.get(k); ++n) {
                elements.add(new int[]{k, n, i++});
            }
        }

        JavaRDD<int[]> rdd = sc.parallelize(elements);
        rdd.foreach(elm -> {
            final int k = elm[0];
            final int d = elm[1];
            final int pos = elm[2];
            final int currentFrames = frames.get(k);
            final int currentAccel = accel.get(k);
            final double[] trans1 = transforms.get(k - 1);
            final double[] trans2 = transforms.get(k);

            AffineTransform3D transformStart = new AffineTransform3D();
            transformStart.set(trans1);
            AffineTransform3D transformEnd = new AffineTransform3D();
            transformEnd.set(trans2);
            String file = String.format("%s/img-%04d.png", outputFolder, pos);

            final RandomAccessibleIntervalMipmapSource<?> mipmapSource = OpenNonVolatile.createMipmapSource(n5Path, n5Group);
            oneFrame(
                    mipmapSource,
                    transformStart,
                    transformEnd,
                    currentFrames,
                    currentAccel,
                    d,
                    width,
                    height,
                    file);
            System.out.println("saved:" + file);

        });

        System.out.println("done, took: " + (System.currentTimeMillis() - time) + " ms.");
        return null;
    }

    public static void main(String[] args) throws IOException {
        System.out.println(Arrays.toString(args));
        System.exit(new CommandLine(new DistributedMovieProducer()).execute(args));
    }

    public static void oneFrame(
            final RandomAccessibleIntervalMipmapSource<?> mipmapSource,
            final AffineTransform3D transformsStart,
            final AffineTransform3D transformsEnd,
            final int frames,
            final int accel,
            final int current,
            int width,
            int height,
            final String outputFile) throws IOException {

        final BdvStackSource<?> bdv = BdvFunctions.show(mipmapSource, BdvOptions.options().numRenderingThreads((Runtime.getRuntime().availableProcessors() - 1) / 2));

        ViewerPanel viewer = bdv.getBdvHandle().getViewerPanel();

        final ViewerState renderState = viewer.state();
        final ScaleBarOverlayRenderer scalebar = new ScaleBarOverlayRenderer();

        int screenWidth = viewer.getDisplayComponent().getWidth();
        int screenHeight = viewer.getDisplayComponent().getHeight();
        double ratio = Math.min(width * 1.0 / screenWidth, height * 1.0 / screenHeight);

        final AffineTransform3D viewerScale = new AffineTransform3D();

        viewerScale.set(
                ratio, 0, 0, 0,
                0, ratio, 0, 0,
                0, 0, 1.0, 0);

        final MultiBoxOverlayRenderer box = new MultiBoxOverlayRenderer(width, height);

        final MovieProducer.Target target = new MovieProducer.Target(width, height);

        final MultiResolutionRenderer renderer = new MultiResolutionRenderer(
                target,
                new PainterThread(null),
                new double[]{1.0},
                0l,
                12,
                null,
                false,
                viewer.getOptionValues().getAccumulateProjectorFactory(),
                new CacheControl.Dummy());

        final SimilarityTransformAnimator animator = new SimilarityTransformAnimator(
                transformsStart,
                transformsEnd,
                0,
                0,
                0);

        final AffineTransform3D tkd = animator.get(accel((double) current / (double) frames, accel));

        tkd.preConcatenate(viewerScale);
        viewer.state().setViewerTransform(tkd);
        renderState.setViewerTransform(tkd);
        renderer.requestRepaint();
        try {
            renderer.paint(renderState);
        } catch (final Exception e) {
            e.printStackTrace();
            return;
        }

        final BufferedImage bi = target.renderResult.getBufferedImage();

        final Graphics2D g2 = bi.createGraphics();
        g2.drawImage(bi, 0, 0, null);

        /* scalebar */
        g2.setClip(0, 0, width, height);
        scalebar.setViewerState(renderState);
        scalebar.paint(g2);
        box.setViewerState(renderState);
        box.paint(g2);

        /* save image */
        ImageIO.write(bi, "png", new File(outputFile));

        bdv.close();

    }
}
