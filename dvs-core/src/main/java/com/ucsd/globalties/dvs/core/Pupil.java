package com.ucsd.globalties.dvs.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import com.ucsd.globalties.dvs.core.tools.Pair;

/**
 * Pupil class represents a detected pupil.
 * It has a white dot and a crescent, which are used for disease detection algorithms.
 * @author Rahul
 *
 */
@Slf4j
public class Pupil {
  
  @Getter
  private Eye eye; // the Eye from which this Pupil was derived
  @Getter
  /*Mat is a n-dimensional dense array that can store images, 2d complex arrays,
   * or a matrix of 16-bit signed integers for algebraic operations*/
  private Mat mat;
  
  private WhiteDot whiteDot;

  /*Important Values used in the code*/

  /*RBG values*/
  public int WHITE = 255;
  /*This value might need to be alternated to allow the threshold effect to make the cresent
   * more prominent*/
  public int GRAY = 240;
  public int BLACK = 0;

  /*Values for contouring*/
  public int fillCONTOURS = -1;
  public int contourTHICKNESS = -1;

  /*Values for the circle*/
  public int circleTHICKNESS = 1;

  
  public Pupil(Eye eye, Mat mat) {
    this.eye = eye;
    this.mat = mat;
  }

  /**
   * Detect the white dot here. The idea is to return a double value (or maybe a
   * simple object that describes the white dot's size/position/necessary
   * information to detect diseases. I don't think we really need to crop it out
   * of the image; the positional information will probably suffice.
   * 
   * @return a WhiteDot object identifying the white dot's positional information 
   * relative to the pupil
   */
  public WhiteDot getWhiteDot() {
    if (whiteDot != null) {
      return whiteDot;
    }
    /*random code so that debug output will not override each other*/
    int code = (new Random()).nextInt();
    
    /*Creating the image container to hold the imported image*/
    Mat src = new Mat();
    /*Copies the data from the private mat variable into src*/
    mat.copyTo(src);
    /*This will hold the gray-scaled image*/
    Mat gray = new Mat();

    /*Converts the image from src into gray-scale and stores it into gray*/
    Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
    
    /*This is the test image to test if the image will be converted to grayscale*/
    //Highgui.imwrite("gray-test.jpg", gray);
    
    /*Applies the threshold effect on the image for white values. Takes the image in
     * gray and detects pixels of GRAY and turns it WHITE, and it stores it back to gray.*/
    Double thresh = Imgproc.threshold(gray, gray, GRAY, WHITE, Imgproc.THRESH_BINARY);
    
    /*Test that checks if the image has the threshold effect applied*/
    //Highgui.imwrite("thresh-test.jpg", gray);
    
    /*Creating a list to hold the values of the detected contours. Each contour 
     * found will be stored as a vector of points*/
    List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
    
    /*This will find the contours in a cloned gray and store the points of those contours 
     * in contours list.
     * Imgproc.RETR_LIST = 1; Retrieves all of the contours without establishing any hierarchical
     * relationships
     * Imgproc.CHAIN_APPROX_SIMPLE = 2; Stores absolutely all the contour points.
     * These are static final int constants defined in Imgproc object.
     * */
    Imgproc.findContours(gray.clone(), contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
    
    /*This draws the draws contour outlines in the image if thickness >= 0 or fills the area 
     * bounded by the contours if thickness<0. thickness is last parameter. fillCONTOURS will allow
     * all the contours to be drawn.*/
    Imgproc.drawContours(gray, contours, fillCONTOURS, new Scalar(WHITE, WHITE, WHITE), contourTHICKNESS);

    /*If the contours list has nothing in it, then it means that the patient does not have contours in
     * their image. Stop function.*/
    if (contours.isEmpty()) {
      log.error("No contours found for this pupil.");
      return null;
    }


    /*Creating a point representing a location in (x,y) coordinate space, specified in integer precision.
     * This sets up a pointer to point to the very center of the image. As you can see, we have x point
     * to half of mat(the image) and for the y axis to half of mat's height. TODO*/
    java.awt.Point pupilCenter = new java.awt.Point(mat.width() / 2, mat.height() / 2);


    /*List holding the distants in the contours. This will hold pairs(left, right), where left is the contour 
     * and right is the contour's distance from the center of the pupil*/
    List<Pair<MatOfPoint, Double>> contourDistances = new ArrayList<>(contours.size());

    /*For-loop will go through the contours list and evaluate each of the contour points found in the list.
     * It will store the distance (in contourDistances) it finds between the center of the pupil to the 
     * contours it locates. */
    for (int i = 0; i < contours.size(); i++) {

      /*Creates rectangle object. boundingRect calculates the up-right bounding rectangle of a point set using
       * the value count in contours.*/
      Rect rect = Imgproc.boundingRect(contours.get(i));

      /*To obtain the radius for the circle*/
      int radius = rect.width / 2;

      /*Creates a circle using the information from the rectangle object.
       * circle(Mat img, Point center, int radius, Scalar color, int thickness).
       * Thickness is the outline of the circle.*/
      Core.circle(src, new Point(rect.x + rect.width / 2, rect.y + rect.width / 2), radius,
       new Scalar(WHITE, BLACK, BLACK), circleTHICKNESS);
      
      /*Points to the center of the circle*/
      java.awt.Point center = new java.awt.Point(rect.x + radius, rect.y + radius);

      /*Gets the distance between the pupil to the contour and stores it as pairs in the 
       * contourDistance list. First element is the value of the contour, then the second is
       * the distance between the pupil center to the contour.*/
      contourDistances.add(new Pair<>(contours.get(i), pupilCenter.distanceSq(center)));
    }

    /*sort the contours based on the distance from the center of the pupil (ascending)*/
    contourDistances.sort(contourCompare);

    /*Empty pair object*/
    Pair<MatOfPoint, Double> whiteDotPair = null;
    
    
    /*For-each loop: For each pair found in the contourDistances list, find the closest contour that matches
     * certain criteria (currently checks for size)*/
    for (Pair<MatOfPoint, Double> pair: contourDistances) {

      /* pair.getLeft() is the contour and pair.getRight() is the contour's distance from the 
       * center of the pupil*/
      /*This calculates the contour area and stores it into area*/
      double area = Imgproc.contourArea(pair.getLeft());

      /*This will print out that the white dot is currently at the contour's distance from the center of
       * the pupil. It will also tell us the current area of the contour*/
      log.info("whiteDot distance: {}, area: {}", pair.getRight(), area);
      
      /*NEEDS TUNING: This is suppose to check the bounds*/
      if (area < 10 || area > 200.0) {
        /*If the area falls between these ranges, then reiterate up the loop and don't evaluate whats
         * below this if statement.*/
        continue;
      }

      /*If the area doesn't call between those ranges, then continue onto the loop and break.*/

      /*Stores the pair with the information about the contour's area and the distance between the 
       * contour and the center of the pupil into the whiteDotPair.*/
      whiteDotPair = pair;

      /*Prints out information about the area of the found contour.*/
      log.info("selected pair with area: " + area);

      /*Escape the for-loop*/
      break;
    }

    /*If whiteDotPair is null, meaning that the area was never within the correct ranges, then we can't
     *  detect the white dot in the eye.*/
    if (whiteDotPair == null) {
      log.error("[WhiteDot Detection] Unable to find suitable white dot");
      return null;
    }

    /* whiteDotPair.getLeft() is the contour(of type MatOfPoint) and whiteDotPair.getRight() is the contour's 
     * distance from the center of the pupil*/
    /*assume white dot is the contour closest to the center of the image*/
    MatOfPoint whiteDotContour = whiteDotPair.getLeft(); 

    /*DEBUGGING TEST*/
    /*This creates a retangle by calculating the up-right bounding rectangle of a point set.
     * The function calculates and returns the minimal up-right bounding rectangle for the 
     * specified point set.
     * Basically, creating a retangle out of the value of the contour*/
    Rect rect = Imgproc.boundingRect(whiteDotContour);

    /*Tester*/
    //Highgui.imwrite("test" + code + ".jpg", src);

    /*Calculates area by pi * ((rectangle's width / 2)^2) */
    double wdarea = Math.PI * Math.pow(rect.width / 2, 2);

    //need: distance from white dot center to pupil center,
    //      angle between white dot center and pupil center

    /*Radius to center*/
    int radius = rect.width / 2;

    /*Make new pointer point to the center of the rectangle*/
    java.awt.Point whiteDotCenter = new java.awt.Point(rect.x + radius, rect.y + radius);

    /*Calculates distance between the pupil center and the white dot*/
    double distance = pupilCenter.distance(whiteDotCenter);

    /*Calculate the difference in the distance between the white dot and the pupil center along the x-axis*/
    double xDist = whiteDotCenter.x - pupilCenter.x;
    
    /*If the xdistance is greater than the distance between the pupil center and the white dot is greater,
     * then it means that the distance calculation was incorrect*/
    if (xDist > distance) {
      log.error("[WhiteDot Detection] unfulfilled invariant: adjacent edge of triangle is bigger than hypotenuse");
      return null;
    }
    /*Print out information about the x distance and the distance between pupil center and white dot.*/
    log.info("[WhiteDot Detection] Computing angle for xDist: {}, dist: {}", xDist, distance);
    /*Calculates the arccosine of the x-distance and the distance to find the y-distance or height.*/
    double angle = Math.acos(xDist / distance);

    /*Print information about the white dot detection*/
    log.info("[WhiteDot Detection] computed white dot with distance: {}, angle: {}, area: {}", distance, 
      Math.toDegrees(angle), wdarea);
    /*Sets current info about white dot to a WhiteDot object (defined in WhiteDot.java)*/
    this.whiteDot = new WhiteDot(distance, wdarea, angle);
    return whiteDot;
  }

  private static Comparator<Pair<MatOfPoint, Double>> contourCompare = new Comparator<Pair<MatOfPoint, Double>>() {
    public int compare(Pair<MatOfPoint, Double> p1, Pair<MatOfPoint, Double> p2) {
      return p1.getRight() < p2.getRight() ? -1 : 1;
    }
  };

  /**
   * Return crescent information.
   * TODO when better pictures are taken
   * @return
   */
  public double getCrescent() {
	
    return 0;
  }
  
  /**
   * Return the area of the DETECTED pupil.
   * This is NOT the area of the pupil itself, but usually of the iris.
   * This method does not return the exact value because we divide an int by 2,
   * but we need the area as a relative measure for the white dot, so the exact
   * value is not necessary as long as the white dot area is computed in the same inexact manner.
   * @return an approximation of the area of the Mat identifying this pupil.
   * Margin of error: [0, 0.5) (between 0 (inclusive) and 0.5 (exclusive))
   */
  public double getArea() {
    double radius = mat.width() / 2;
    return Math.PI * Math.pow(radius, 2);
  }
}