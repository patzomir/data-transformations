package com.ontotext.ehri.georecon.place;

import com.ontotext.ehri.georecon.Tools;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A place from GeoNames.
 */
public class Place implements Comparable<Place>, Serializable {

    // the root of the place tree: http://www.geonames.org/6295630/ (Earth)
    public static final Place ROOT = new Place(6295630, 0, 0, 6814400000L, PlaceType.L, null);

    // prefix and suffix to add to the GeoNames ID when constructing the GeoNames URL
    public static final String URL_PREFIX = "http://sws.geonames.org/";
    public static final String URL_SUFFIX = "/";

    // string which separates places in a lineage representation
    private static final String LINEAGE_SEPARATOR = " => ";

    // coordinates of reference point: http://sws.geonames.org/2950159/ (Berlin)
    private static final double REF_POINT_LAT = 52.52437;
    private static final double REF_POINT_LON = 13.41053;

    private int geoID;
    private double latitude, longitude;
    private long population;
    private PlaceType type;
    private Place parent;

    public Place(int geoID, double latitude, double longitude, long population, PlaceType type, Place parent) {
        this.geoID = geoID;
        this.latitude = latitude;
        this.longitude = longitude;
        this.population = population;
        this.type = type;
        this.parent = parent;
    }

    public int getGeoID() {
        return geoID;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public long getPopulation() {
        return population;
    }

    public PlaceType getType() {
        return type;
    }

    public Place getParent() {
        return parent;
    }

    /**
     * Test if this place is a descendant of some other place. A place cannot be a descendant of itself.
     * @param other The other place.
     * @return True if this place is descendant of the other place; false otherwise.
     */
    public boolean isDescendantOf(Place other) {
        Place pointer = this;

        while ((pointer = pointer.parent) != null) {
            if (pointer.equals(other)) return true;
        }

        return false;
    }

    /**
     * Test if this place is a sibling of some other place.
     * @param other The other place.
     * @return True if this place is sibling of the other place; false otherwise.
     */
    public boolean isSiblingOf(Place other) {
        Place myParent = parent;
        Place otherParent = other.parent;

        if (myParent != null && otherParent != null && myParent.equals(otherParent)) return true;
        return false;
    }

    /**
     * Get the lineage of this place. This is the sequence which starts with the root of the place tree, includes all
     * ancestors of this place in order from furthest to closest, and ends with this place. In other words, it is the
     * shortest path from the root to this place.
     * @return The lineage of this place.
     */
    public Deque<Place> lineage() {
        Deque<Place> lineage = new LinkedList<Place>();
        Place pointer = this;
        lineage.add(pointer);

        // prepend next ancestor till you hit the root
        while ((pointer = pointer.parent) != null) {
            lineage.addFirst(pointer);
        }

        return lineage;
    }

    /**
     * Get the closest common place between this and some other place. This is the smallest place which includes both
     * this and the other place.
     * @param other The other place.
     * @return The closest common place or null if the two places have no common ancestor (should not happen).
     */
    public Place closestCommon(Place other) {
        Iterator<Place> myLineage = lineage().iterator();
        Iterator<Place> otherLineage = other.lineage().iterator();
        Place closestCommon = null;

        // iterate through both lineages
        while (myLineage.hasNext() && otherLineage.hasNext()) {
            Place myAncestor = myLineage.next();
            Place otherAncestor = otherLineage.next();

            // update closest common till lineages diverge
            if (myAncestor.equals(otherAncestor)) closestCommon = myAncestor;
            else break;
        }

        return closestCommon;
    }

    /**
     * Calculate the distance in the tree between this place and some other place. This is the number of nodes you need
     * to traverse to get to the other place if you follow the shortest path.
     * @param other The other place.
     * @return A positive integer representing the distance in the tree between this and the other place,
     * zero if they are the same place, or negative one if they are not connected (should not happen).
     */
    public int treeDistance(Place other) {
        Iterator<Place> myLineage = lineage().iterator();
        Iterator<Place> otherLineage = other.lineage().iterator();
        Place closestCommon = null;

        // iterate through both lineages till they diverge
        while (myLineage.hasNext() && otherLineage.hasNext()) {
            Place myAncestor = myLineage.next();
            Place otherAncestor = otherLineage.next();

            // update closest common till lineages diverge
            if (myAncestor.equals(otherAncestor)) closestCommon = myAncestor;
            else break;
        }

        if (closestCommon == null) return -1;
        int treeDistance = 0;

        // add the number of diverging nodes
        while (myLineage.hasNext()) {
            myLineage.next();
            treeDistance++;
        }

        // add the number of diverging nodes
        while (otherLineage.hasNext()) {
            otherLineage.next();
            treeDistance++;
        }

        return treeDistance;
    }

    /**
     * Calculate the number of ancestors that this place has.
     * @return The number of ancestors.
     */
    public int numAncestors() {
        int numAncestors = 0;
        Place pointer = this;

        while ((pointer = pointer.parent) != null) {
            numAncestors++;
        }

        return numAncestors;
    }

    /**
     * Get a string representation of the lineage of this place.
     * @return A string representing the lineage of this place.
     */
    public String lineageString() {
        StringBuilder lineageString = new StringBuilder();
        Iterator<Place> lineage = lineage().iterator();

        // append separator and next place in lineage
        while (lineage.hasNext()) {
            lineageString.append(LINEAGE_SEPARATOR);
            lineageString.append(lineage.next().toString());
        }

        // return the string without the first separator
        return lineageString.substring(LINEAGE_SEPARATOR.length());
    }

    /**
     * Construct the GeoNames URL of this place.
     * @return The GeoNames URL of this place.
     */
    public URL toURL() {

        try {
            return new URL(URL_PREFIX + geoID + URL_SUFFIX);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null; // not going to happen
        }
    }

    /**
     * Calculate the distance in kilometers between this place and a point on the map.
     * @param latitude The latitude of the point.
     * @param longitude The longitude of the point.
     * @return The distance in kilometers.
     */
    public double distanceTo(double latitude, double longitude) {
        return Tools.distance(this.latitude, this.longitude, latitude, longitude, "K");
    }

    /**
     * Calculate the distance in kilometers between this place and some other place.
     * @param other The other place.
     * @return The distance in kilometers.
     */
    public double distanceTo(Place other) {
        return distanceTo(other.latitude, other.longitude);
    }

    /**
     * Compare this place to another place.
     * @param o The other place.
     * @return -1 if this place is more relevant; +1 if this place is less relevant; 0 if they are the same.
     */
    public int compareTo(Place o) {
        if (this == o) return 0;
        if (geoID == o.geoID) return 0;

        // prefer countries
        if (type == PlaceType.A_PCL && o.type != PlaceType.A_PCL) return -1;
        else if (type != PlaceType.A_PCL && o.type == PlaceType.A_PCL) return 1;

        // prefer more populated places
        if (population > o.population) return -1;
        else if (population < o.population) return 1;

        // prefer places closer to the reference point
        double myDist = distanceTo(REF_POINT_LAT, REF_POINT_LON);
        double oDist = o.distanceTo(REF_POINT_LAT, REF_POINT_LON);
        if (myDist < oDist) return -1;
        else if (myDist > oDist) return 1;

        // last resort
        return Integer.compare(geoID, o.geoID);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        // two places are equal if they have the same GeoNames ID
        Place place = (Place) o;
        return geoID == place.geoID;
    }

    @Override
    public int hashCode() {
        return geoID;
    }

    @Override
    public String toString() {
        return toURL().toString();
    }
}
