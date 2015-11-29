/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package processing;

import java.util.ArrayList;
import java.util.HashMap;
import org.opencv.core.Mat;
import org.opencv.core.Point;

/**
 *
 * @author Gonzalo
 */
public class ObjectsSetFinder {
    
    private Mat inputFrame = null;
    private ArrayList<ObjectFinder> findersList = null;
    private ArrayList<Point> massCentersList = null;
    private HashMap<String, Boolean> results = null;
   
    public void process(Mat input, Mat output) {
        this.inputFrame = input;
        this.massCentersList = new ArrayList<>();
        for (int i = 0; i < findersList.size(); i++) {
            ObjectFinder finder = findersList.get(i);
            boolean found = finder.process(input, output);
            this.results.put(finder.getObjectName(), found);
            this.massCentersList.add(finder.getMassCenter());
        }
    }        
    
    public ObjectsSetFinder(ArrayList<ObjectFinder> findersList) {
        this.findersList = findersList;
        this.massCentersList = new ArrayList<>();
        this.results = new HashMap<>();
    }
    
    public Mat getInputFrame() {
        return inputFrame;
    }

    public void setInputFrame(Mat inputFrame) {
        this.inputFrame = inputFrame;
    }

    public ArrayList<ObjectFinder> getFindersList() {
        return findersList;
    }

    public void setFindersList(ArrayList<ObjectFinder> findersList) {
        this.findersList = findersList;
    }

    public ArrayList<Point> getMassCentersList() {
        return massCentersList;
    }

    public void setMassCentersList(ArrayList<Point> massCentersList) {
        this.massCentersList = massCentersList;
    }
    
    public HashMap<String, Boolean> getResults() {
        return results;
    }

    public void setResults(HashMap<String, Boolean> results) {
        this.results = results;
    }

}
