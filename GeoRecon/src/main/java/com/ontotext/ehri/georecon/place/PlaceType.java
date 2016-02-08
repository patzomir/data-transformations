package com.ontotext.ehri.georecon.place;

import java.io.Serializable;

/**
 * Relevant place types in order of relevance.
 */
public enum PlaceType implements Serializable {
    A_PCL, P, A, // country < city < division
    S_ADMF, S_BDG, S_CH, S_CMTY, S_HSTS, S_MNMT, S_MUS, S_RUIN, // relevant structures
    H, L, R, T, U, V // various landmarks
}
