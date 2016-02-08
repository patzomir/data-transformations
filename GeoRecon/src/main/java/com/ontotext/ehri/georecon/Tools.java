package com.ontotext.ehri.georecon;

import com.ontotext.ehri.georecon.place.PlaceIndex;

import java.io.*;

/**
 * Helper methods.
 */
public class Tools {

    /**
     * Source: https://www.geodatasource.com/developers/java
     */
    public static double distance(double lat1, double lon1, double lat2, double lon2, String unit) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) +
                Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;

        if (unit == "K") {
            dist = dist * 1.609344;
        } else if (unit == "N") {
            dist = dist * 0.8684;
        }

        return (dist);
    }

    /**
     * Source: https://www.geodatasource.com/developers/java
     */
    private static double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    /**
     * Source: https://www.geodatasource.com/developers/java
     */
    private static double rad2deg(double rad) {
        return (rad * 180 / Math.PI);
    }

    /**
     * Serialize a place index to file.
     * @param index The place index.
     * @param file The file.
     * @throws IOException
     */
    public static void serializeIndex(PlaceIndex index, File file) throws IOException {
        FileOutputStream fileOutput = new FileOutputStream(file);
        ObjectOutputStream objectOutput = new ObjectOutputStream(fileOutput);

        try {
            objectOutput.writeObject(index);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            objectOutput.close();
            fileOutput.close();
        }
    }

    /**
     * Deserialize a place index from file.
     * @param file The file.
     * @return The place index.
     * @throws IOException
     */
    public static PlaceIndex deserializeIndex(File file) throws IOException {
        FileInputStream fileInput = new FileInputStream(file);
        ObjectInputStream objectInput = new ObjectInputStream(fileInput);
        PlaceIndex placeIndex = null;

        try {
            placeIndex = (PlaceIndex) objectInput.readObject();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            objectInput.close();
            fileInput.close();
            return placeIndex;
        }
    }
}
