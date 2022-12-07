package org.janelia.saalfeldlab.n5.bdv.movie;

import bdv.export.ProgressWriterConsole;
import bdv.tools.movie.MovieProducer;
import bdv.tools.movie.preview.MovieFrameInst;
import bdv.tools.movie.serilizers.MovieFramesSerializer;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class OpenNonVolatile {
    private static String n5Path = "/Volumes/flyem/render/n5/Z0720_07m_CNS.n5";
    private static String jsonTransformations = "/Users/zouinkhim/Desktop/test.json";
    private static String n5Group = "/full_cns";
    private static String outputFolder = "/Users/zouinkhim/Desktop/test_video";

    public static RandomAccessibleIntervalMipmapSource<UnsignedByteType> createMipmapSource(
            final String n5Path,
            final String n5Group) throws IOException {

        final N5Reader n5 = new N5FSReader(n5Path);

        final int numScales = 7;
        final RandomAccessibleInterval<UnsignedByteType>[] mipmaps = (RandomAccessibleInterval<UnsignedByteType>[]) new RandomAccessibleInterval[numScales];
        final double[][] scales = new double[numScales][3];

        for (int scaleIndex = 0; scaleIndex < numScales; ++scaleIndex) {

            final int scale = 1 << scaleIndex;
            final double inverseScale = 1.0 / scale;
            final RandomAccessibleInterval<UnsignedByteType> img = N5Utils.openVolatile(n5, n5Group + "/s" + scaleIndex);

			/*
			// TODO
			final int blockRadius = (int)Math.round(511 * inverseScale);
			final ImageJStackOp<UnsignedByteType> cllcn =
					new ImageJStackOp<>(
							Views.extendZero(img),
							(fp) -> new CLLCN(fp).run(blockRadius, blockRadius, 3f, 10, 0.5f, true, true, true),
							blockRadius,
							0,
							255);
			final RandomAccessibleInterval<UnsignedByteType> cllcned = Lazy.process(
					img,
					new int[] {128, 128, 16},
					new UnsignedByteType(),
					AccessFlags.setOf(AccessFlags.VOLATILE),
					cllcn);
			*/
            mipmaps[scaleIndex] = img;//cllcned;
            scales[scaleIndex] = new double[]{scale, scale, scale};
        }

        final RandomAccessibleIntervalMipmapSource<UnsignedByteType> mipmapSource =
                new RandomAccessibleIntervalMipmapSource<>(
                        mipmaps,
                        new UnsignedByteType(),
                        scales,
                        new FinalVoxelDimensions("um", new double[]{0.008, 0.008, 0.008}),
                        "VNC");

        return mipmapSource;
    }


    public static void main(String[] args) throws IOException {

        int width = 800;
        int height = 600;

//        int width = 2560;
//        int height = 1600;
        final RandomAccessibleIntervalMipmapSource<?> mipmapSource = createMipmapSource(n5Path, n5Group);

        final BdvStackSource<?> bdv = BdvFunctions.show(mipmapSource, BdvOptions.options().numRenderingThreads((Runtime.getRuntime().availableProcessors() - 1) / 2));

//        List<MovieFrameInst> movieFrames = MovieFramesSerializer.getFrom(new File(jsonTransformations));
//
//        int size = movieFrames.size();
//
//        final AffineTransform3D[] transforms = new AffineTransform3D[size];
//        final int[] frames = new int[size];
//        final int[] accel = new int[size];
//
//        for (int i = 0; i < size; i++) {
//            MovieFrameInst currentFrame = movieFrames.get(i);
//            transforms[i] = currentFrame.getTransform();
//            frames[i] = currentFrame.getFrames();
//            accel[i] = currentFrame.getAccel();
//        }
//
//        MovieProducer.recordMovie(
//                bdv.getBdvHandle().getViewerPanel(),
//                transforms,
//                frames,
//                accel,
//                width,
//                height,
//                outputFolder,
//                new ProgressWriterConsole());
//        int i  = 0;
//        for (int k = 1; k < transforms.length; ++k) {
//            AffineTransform3D transformStart = transforms[k - 1];
//            AffineTransform3D transformEnd = transforms[k];
//            for (int d = 0; d < frames[k]; ++d) {
//                String file = String.format("%s/img-%04d.png", outputFolder, i++);
//                DistributeProducer.oneFrame(
//                        mipmapSource,
//                        transformStart,
//                        transformEnd,
//                        frames[k],
//                        accel[k],
//                        d,
//                        width,
//                        height,
//                        file);
//            }
//        }


    }
}
