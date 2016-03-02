package com.ontotext.tybus;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Comparator which sorts tokens from least frequent to most frequent.
 */
public class FrequencyComparator implements Comparator<Token>, Serializable {

    @Override
    public int compare(Token one, Token two) {
        int comparison = Integer.compare(one.getNumOccurrences(), two.getNumOccurrences());
        if (comparison != 0) return comparison;
        return one.compareTo(two);
    }
}
