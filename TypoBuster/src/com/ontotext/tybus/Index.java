package com.ontotext.tybus;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;

/**
 * An index maps typos to their corrections.
 *
 * A typo differs from its correction in one of four ways:
 *  - alteration: one character is changed into another character (e.g. "string" becomes "strung"),
 *  - transposition: two adjacent characters are swapped (e.g. "string" becomes "stirng"),
 *  - deletion: one character is removed (e.g. "string" becomes "sting"),
 *  - insertion: one character is added (e.g. "string" becomes "strring").
 *
 * Furthermore, the typo and its correction must satisfy these criteria:
 *  - both must have some minimum length,
 *  - the correction must have some minimum frequency,
 *  - the typo must have a lower frequency than its correction,
 *  - all differing characters are lowercase letters.
 */
public class Index implements Serializable {

    // default number of corrections to make
    private static final int DEFAULT_NUM_CORRECTIONS = 1;

    // minimum length of correction or typo
    private static final int MIN_LENGTH = 4;

    // minimum frequency of correction
    private static final int MIN_CORRECTION_FREQUENCY = 4;

    // typo-frequency to correction-frequency ratio
    private static final float TYPO_FREQUENCY_RATIO = 0.5f;

    private Map<String, String> typo2correction;

    /**
     * Build a new index from a model.
     * @param model The model to build from.
     */
    public Index(Model model) {
        int maxLength = model.maxTokenLength();
        typo2correction = new HashMap<>();

        // iterate through tokens by length
        for (int length = MIN_LENGTH; length <= maxLength; length++) {
            NavigableSet<Token> tokens = model.getTokens(length);
            Iterator<Token> correctionIterator = tokens.descendingIterator();

            // iterate through valid corrections
            while (correctionIterator.hasNext()) {
                Token correctionToken = correctionIterator.next();
                int correctionFrequency = correctionToken.getNumOccurrences();
                if (correctionFrequency < MIN_CORRECTION_FREQUENCY) break;

                // calculate the maximum typo frequency
                int maxTypoFrequency = Math.round(TYPO_FREQUENCY_RATIO * correctionFrequency);
                String correction = correctionToken.getContent();

                // find same-length typos
                for (Token typoToken : tokens) {
                    if (typoToken.getNumOccurrences() > maxTypoFrequency) break;

                    // check for alteration or transposition
                    String typo = typoToken.getContent();
                    if (isAlterationOrTransposition(typo, correction)) typo2correction.put(typo, correction);
                }

                // find lower-length typos
                if (length > MIN_LENGTH) {

                    for (Token typoToken : model.getTokens(length - 1)) {
                        if (typoToken.getNumOccurrences() > maxTypoFrequency) break;

                        // check for deletion
                        String typo = typoToken.getContent();
                        if (isDeletion(typo, correction)) typo2correction.put(typo, correction);
                    }
                }

                // find greater-length typos
                if (length < maxLength) {

                    for (Token typoToken : model.getTokens(length + 1)) {
                        if (typoToken.getNumOccurrences() > maxTypoFrequency) break;

                        // check for insertion
                        String typo = typoToken.getContent();
                        if (isInsertion(typo, correction)) typo2correction.put(typo, correction);
                    }
                }
            }
        }
    }

    /**
     * Correct a typo.
     * @param typo The typo to correct.
     * @return The correction of the typo if such exists. Otherwise the typo.
     */
    public String correct(String typo) {
        return correct(typo, DEFAULT_NUM_CORRECTIONS);
    }

    /**
     * Correct a typo up to a given number of times.
     * @param typo The typo to correct.
     * @param maxNumCorrections The maximum number of corrections to make.
     * @return The correction of the typo if such exists. Otherwise the typo.
     */
    public String correct(String typo, int maxNumCorrections) {
        String result = typo;

        // perform corrections till limit or till no more corrections possible
        for (int numCorrection = 0; numCorrection < maxNumCorrections; numCorrection++) {
            String correction = typo2correction.get(result);
            if (correction == null) return result;

            // update result
            result = correction;
        }

        return result;
    }

    /**
     * Correct a typo till no more corrections exist.
     * @param typo The typo to correct.
     * @return The correction of the typo if such exists. Otherwise the typo.
     */
    public String correctFully(String typo) {
        String result = typo;
        String correction;

        // perform corrections till no more corrections possible
        while ((correction = typo2correction.get(result)) != null) {
            result = correction;
        }

        return result;
    }

    /**
     * Check if a string is an alteration or transposition of another string.
     * The two strings are assumed to have equal lengths.
     * @param one A string.
     * @param two A string.
     * @return True if one of the string is an alteration or transposition of the other string. False otherwise.
     */
    private static boolean isAlterationOrTransposition(String one, String two) {
        int maxLength = one.length();
        int commonPrefixLength = commonPrefixLength(one, two, maxLength);
        int commonSuffixLength = commonSuffixLength(one, two, maxLength);
        int sumCommonLengths = commonPrefixLength + commonSuffixLength;

        // check for alteration
        if (sumCommonLengths == maxLength - 1 &&
                isValidChar(one.charAt(commonPrefixLength)) &&
                isValidChar(two.charAt(commonPrefixLength))) return true;

        // check for transposition
        if (sumCommonLengths == maxLength - 2 &&
                one.charAt(commonPrefixLength) == two.charAt(commonPrefixLength + 1) &&
                one.charAt(commonPrefixLength + 1) == two.charAt(commonPrefixLength) &&
                isValidChar(one.charAt(commonPrefixLength)) &&
                isValidChar(one.charAt(commonPrefixLength + 1))) return true;

        return false;
    }

    /**
     * Check if the first string is a deletion of the second string.
     * The first string is assumed to be one character shorter than the second string.
     * @param deletion The shorter string.
     * @param original The longer string.
     * @return True if the first string is a deletion of the second string. False otherwise.
     */
    private static boolean isDeletion(String deletion, String original) {
        int maxLength = deletion.length();
        int commonPrefixLength = commonPrefixLength(deletion, original, maxLength);
        int commonSuffixLength = commonSuffixLength(deletion, original, maxLength);
        int sumCommonLengths = commonPrefixLength + commonSuffixLength;

        // check for deletion
        if ((sumCommonLengths == deletion.length() || sumCommonLengths == original.length()) &&
                isValidChar(original.charAt(commonPrefixLength))) return true;

        return false;
    }

    /**
     * Check if the first string is an insertion of the second string.
     * The first string is assumed to be one character longer than the second string.
     * @param insertion The longer string.
     * @param original The shorter string.
     * @return True if the first string is an insertion of the second string. False otherwise.
     */
    private static boolean isInsertion(String insertion, String original) {
        return isDeletion(original, insertion);
    }

    /**
     * Check if a character is a valid typo character.
     * @param c A character.
     * @return True if the character is a valid typo character. False otherwise.
     */
    private static boolean isValidChar(char c) {
        return Character.isLowerCase(c);
    }

    /**
     * Get the length of the common prefix between two strings.
     * @param one A string.
     * @param two A string.
     * @param maxLength The maximum length for the common prefix.
     * @return The length of the common prefix.
     */
    private static int commonPrefixLength(String one, String two, int maxLength) {

        // return index of divergence
        for (int index = 0; index < maxLength; index++) {
            if (one.charAt(index) != two.charAt(index)) return index;
        }

        return maxLength;
    }

    /**
     * Get the length of the common suffix between two strings.
     * @param one A string.
     * @param two A string.
     * @param maxLength The maximum length for the common suffix.
     * @return The length of the common suffix.
     */
    private static int commonSuffixLength(String one, String two, int maxLength) {

        // return index of divergence starting from the ends
        for (int offset = 0; offset < maxLength; offset++) {
            int oneIndex = one.length() - 1 - offset;
            int twoIndex = two.length() - 1 - offset;
            if (one.charAt(oneIndex) != two.charAt(twoIndex)) return offset;
        }

        return maxLength;
    }
}
