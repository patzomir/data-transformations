package com.ontotext.ehri.georecon.place;

import java.io.Serializable;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Index of GeoNames places for fast lookup.
 */
public class PlaceIndex implements Serializable {

    // regular expressions used in name normalization
    private static final Pattern DIACR_SEQ = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern PUNCT_SEQ = Pattern.compile("\\p{Punct}+");
    private static final Pattern SPACE_SEQ = Pattern.compile("\\s+");

    // map from names to places
    private Map<String, Set<Place>> index;

    /**
     * Construct an empty place index.
     */
    public PlaceIndex() {
        index = new HashMap<String, Set<Place>>();
    }

    /**
     * Add a place to the index.
     * @param place The place to add.
     * @param name The name of the place.
     */
    public void add(Place place, String name) {
        name = normalizeName(name);

        // retrieve set of places for this name
        Set<Place> places = index.get(name);
        if (places == null) places = new TreeSet<Place>();

        // add place to set of places for this name
        places.add(place);
        index.put(name, places);
    }

    /**
     * Get the set of places with the given name.
     * @param name The name of the places.
     * @return The set of places with this name, or null if there are no places with this name.
     */
    public Set<Place> get(String name) {
        name = normalizeName(name);
        return index.get(name);
    }

    /**
     * Get one place with the given name.
     * @param name The name of the place.
     * @return The most relevant place with this name, or null if there are no places with this name.
     */
    public Place getOne(String name) {
        Set<Place> matches = get(name);
        if (matches == null) return null;
        return matches.iterator().next();
    }

    /**
     * Normalize a place name for easier string matching.
     * @param name The name of the place.
     * @return The normalized name.
     */
    private static String normalizeName(String name) {
        name = Normalizer.normalize(name, Normalizer.Form.NFKD);
        name = DIACR_SEQ.matcher(name).replaceAll("");
        name = PUNCT_SEQ.matcher(name).replaceAll(" ");
        name = SPACE_SEQ.matcher(name).replaceAll(" ");
        name = name.trim();
        name = name.toLowerCase();
        return name;
    }
}
