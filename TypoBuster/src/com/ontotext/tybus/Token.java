package com.ontotext.tybus;

import java.io.Serializable;

/**
 * A token stores an atomic piece of text (e.g. word or phrase) and its frequency.
 */
public class Token implements Comparable<Token>, Serializable {
    private String content;
    private int numOccurrences;

    /**
     * Create a new token from a piece of text.
     * @param content The string content of the token.
     */
    public Token(String content) {
        this.content = content;
        numOccurrences = 1;
    }

    public String getContent() {
        return content;
    }

    public int getNumOccurrences() {
        return numOccurrences;
    }

    /**
     * Add an occurrence of this token.
     */
    public void addOccurrence() {
        numOccurrences++;
    }

    @Override
    public int compareTo(Token other) {
        if (this == other) return 0;

        if (numOccurrences < other.numOccurrences) return -1;
        if (numOccurrences > other.numOccurrences) return 1;
        return content.compareTo(other.content);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;

        Token token = (Token) other;
        if (numOccurrences != token.numOccurrences) return false;
        return content.equals(token.content);
    }

    @Override
    public int hashCode() {
        int result = content.hashCode();
        result = 31 * result + numOccurrences;
        return result;
    }

    @Override
    public String toString() {
        return numOccurrences + " \"" + content + "\"";
    }
}
