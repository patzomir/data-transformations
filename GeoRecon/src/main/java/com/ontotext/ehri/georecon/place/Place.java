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
     * Test if this place is a descendant of some other place.
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
     * Get the ancestors of this place.
     * @return The ancestors in order from closest to furthest.
     */
    public Deque<Place> getAncestors() {
        Deque<Place> ancestors = new LinkedList<Place>();
        Place pointer = this;

        while ((pointer = pointer.parent) != null) {
            ancestors.add(pointer);
        }

        return ancestors;
    }

    /**
     * Find the closest common ancestor between this place and some other place.
     * @param other The other place.
     * @return The closest common ancestor.
     */
    public Place closestCommonAncestor(Place other) {
        Deque<Place> ancestors = getAncestors();
        Iterator<Place> iterator = ancestors.descendingIterator();
        Deque<Place> otherAncestors = other.getAncestors();
        Iterator<Place> otherIterator = otherAncestors.descendingIterator();
        Place closestCommonAncestor = null;

        // iterate through both ancestral lines
        while (iterator.hasNext() && otherIterator.hasNext()) {
            Place ancestor = iterator.next();
            Place otherAncestor = otherIterator.next();

            // update closest common ancestor and return it if the ancestral lines diverge
            if (ancestor.equals(otherAncestor)) closestCommonAncestor = ancestor;
            else return closestCommonAncestor;
        }

        // check if one of the places is an ancestor of the other place
        if (iterator.hasNext()) {
            Place ancestor = iterator.next();
            if (ancestor.equals(other)) closestCommonAncestor = ancestor;
        } else if (otherIterator.hasNext()) {
            Place otherAncestor = otherIterator.next();
            if (equals(otherAncestor)) closestCommonAncestor = this;
        }

        return closestCommonAncestor;
    }

    /**
     * Return a string representing the ancestry of this place.
     * @return A string representing the ancestry of this place.
     */
    public String ancestry() {
        StringBuilder ancestry = new StringBuilder();
        StringBuilder indent = new StringBuilder();
        Deque<Place> ancestors = getAncestors();
        Iterator<Place> ancestorIterator = ancestors.descendingIterator();

        while (ancestorIterator.hasNext()) {
            Place ancestor = ancestorIterator.next();
            ancestry.append(indent.toString());
            ancestry.append(ancestor.toURL().toString());
            ancestry.append("\n");
            indent.append("  ");
        }

        ancestry.append(indent.toString());
        ancestry.append(toURL().toString());
        return ancestry.toString();
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

        // compare types
        int compareType = type.compareTo(o.type);
        if (compareType != 0) return compareType;

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
        final StringBuilder sb = new StringBuilder("Place{");
        sb.append("geoID=").append(geoID);
        sb.append(", latitude=").append(latitude);
        sb.append(", longitude=").append(longitude);
        sb.append(", population=").append(population);
        sb.append(", type=").append(type);
        sb.append(", parent=").append(parent);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Construct the GeoNames URL of this place.
     * @return The GeoNames URL of this place.
     */
    public URL toURL() {

        try {
            return new URL("http://sws.geonames.org/" + geoID + "/");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null; // not going to happen
        }
    }
}
