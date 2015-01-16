import org.opencv.highgui.Highgui;
import org.opencv.core.Core;
import org.opencv.core.Rect;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfFloat;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

class Mosiac {

  static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

  static final int IMAGE_DIVISOR = 20;
  static final int BIN_SIZE = 50;
  static final int HISTOGRAM_DIVISOR = 3;
  static final ImageComparitor comparitor = new ChiSquareComparitor();
  static final BiFunction<Mat, Mat, Double> COMPARE = comparitor::compare;

  public static Mat generateHistogram(Mat img) {
    // images are all square
    MatOfInt channels = new MatOfInt(0, 1, 2);
    Mat hist = new Mat();
    MatOfInt histSize = new MatOfInt(BIN_SIZE, BIN_SIZE, BIN_SIZE);
    MatOfFloat histRange = new MatOfFloat(0f, 256f, 0f, 256f, 0f, 256f);
    Imgproc.calcHist(Arrays.asList(img), channels, new Mat(), hist, histSize, histRange, false);
    Core.normalize(hist, hist);
    return hist;
  }

  public static List<Mat> generateHistograms(Mat img) {
  	return divideImage(img, HISTOGRAM_DIVISOR)
      .parallelStream()
      .map(tileImg -> generateHistogram(tileImg))
      .collect(Collectors.toList());
  }

  public static List<Mat> divideImage(Mat img, int divisor) {
    // images are all square
    int size = img.width() / divisor;
    List<Mat> imgs = new ArrayList<Mat>();
    for(int row=0 ; row < divisor ; row++) {
      for(int col=0 ; col < divisor ; col++) {
        imgs.add(new Mat(img, new Rect(row*size, col*size, size, size)));
      }
    }
    return imgs;
  }

  private static Mat openImage(String path) {
    Mat img = Highgui.imread(path, Highgui.CV_LOAD_IMAGE_COLOR);
    if(img.empty())
      return null;
    //Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2HSV);
    return img;
  }

  public static void main(String[] args) throws Exception {
    Mat sourceImg = openImage("face.jpg");
    List<List<Mat>> sourceTilesHistos = new ArrayList<List<Mat>>();
    for(Mat sourceTile : divideImage(sourceImg, IMAGE_DIVISOR)) {
    	List<Mat> histos = generateHistograms(sourceTile);
    	//Mat h = generateHistogram(openImage("images/10890549_1540436706232580_50705274_s.jpg"));
      //Imgproc.compareHist(histos.get(0), h, Imgproc.CV_COMP_CORREL);
    	sourceTilesHistos.add(histos);
    }

    System.out.print("Loading histograms.");
    Map<Mat, List<Mat>> imgHistoGroups = new HashMap<>();
    Files.walk(Paths.get("images/")).forEach(filePath -> {
      if (Files.isRegularFile(filePath)) {
        Mat img = openImage(filePath.toString());
        if(img == null)
          return;
        imgHistoGroups.put(img, generateHistograms(img));
      }
    });

    System.out.print("Loaded histograms.");
    AtomicInteger progress = new AtomicInteger (0);
    List<Mat> outputImages = sourceTilesHistos
      .parallelStream()
      .map(sourceTileHistos -> {
      Double similarity = null;
      Mat similiarImg = null;

      for(Map.Entry<Mat, List<Mat>> m : imgHistoGroups.entrySet()) {
        Mat img = m.getKey();
        List<Mat> histoGroup = m.getValue();
        Double sim = 0d;

        for(int i=0 ; i < histoGroup.size() ; i++) {
          sim += COMPARE.apply(img, histoGroup.get(i));
        }

        if(similarity == null || sim < similarity) {
          similarity = sim;
          similiarImg = img;
        }

        int p = progress.incrementAndGet();
        System.out.println("similiar image found for " + p + "/" + IMAGE_DIVISOR);
      }
      
      return similiarImg;
    }).collect(Collectors.toList());

    List<Mat> outputImageRows = IntStream.range(1, IMAGE_DIVISOR) 
      .mapToObj(i -> {
        Mat img = new Mat();
        List<Mat> row = outputImages.subList((i-1) * IMAGE_DIVISOR, i * IMAGE_DIVISOR);
        Core.vconcat(row, img);
        return img;
      }).collect(Collectors.toList());
    
    Mat outputImage = new Mat();
    Core.hconcat(outputImageRows, outputImage);

    Highgui.imwrite("out.png", outputImage);
  }
}

interface ImageComparitor {
  default public Double compare(Mat img1, Mat img2) {
    Double score = Imgproc.compareHist(img1, img2, getType()); 
    return normalize(score);
  }
  default private Double normalize(Double score) {return score;}
  
  private int getType();
}

class ChiSquareComparitor implements ImageComparitor {
  private int getType() {return Imgproc.CV_COMP_CHISQR;}
}

class Corelation implements ImageComparitor {
  private int getType() {return Imgproc.CV_COMP_CORREL:}
  private Double normalize(Double score) {return (-1 * score) + 1;}
}

class IntersectComparitor implements ImageComparitor {
  private int getType() {return Imgproc.CV_COMP_INTERSECT;}
  private Double normalize(Double score) {return Math.abs(score-1);}
}

class HellingerComparitor implements ImageComparitor {
  private int getType() {return ImgProc.CV_COMP_HELLINGER;}
}
