package com.ontotext.tybus;

import java.io.Serializable;
import java.util.*;

/**
 * A model stores tokens extracted from texts.
 */
public class Model implements Serializable {
    private List<NavigableSet<Token>> length2tokens;
    private Map<String, Token> string2token;

    /**
     * Initialize an empty model.
     */
    public Model() {
        length2tokens = new ArrayList<>();
        string2token = new HashMap<>();
    }

    /**
     * Add a token to the model.
     * @param content The string content of the token.
     */
    public void addToken(String content) {
        if (content == null || content.length() == 0) return;

        int length = content.length();
        Token token = string2token.get(content);

        // token is new
        if (token == null) {
            token = new Token(content);

            // extend list if necessary
            while (length2tokens.size() < length) {
                length2tokens.add(new TreeSet<>());
            }

            // token is known
        } else {
            length2tokens.get(length - 1).remove(token);
            token.addOccurrence();
        }

        length2tokens.get(length - 1).add(token);
        string2token.put(content, token);
    }

    /**
     * Retrieve the sorted tokens with the given length.
     * @param length The length of the tokens.
     * @return The set of tokens with the given length, from least frequent to most frequent.
     */
    public NavigableSet<Token> getTokens(int length) {
        return length2tokens.get(length - 1);
    }

    /**
     * Get the length of the longest token in this model.
     * @return The length of the longest token in this model.
     */
    public int maxTokenLength() {
        return length2tokens.size();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();

        for (Token token : string2token.values()) {
            stringBuilder.append(token.toString() + "\n");
        }

        return stringBuilder.toString();
    }
}
