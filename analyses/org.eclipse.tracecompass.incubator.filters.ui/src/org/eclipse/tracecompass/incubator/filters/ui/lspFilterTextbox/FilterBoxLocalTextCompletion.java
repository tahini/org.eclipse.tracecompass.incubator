/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.filters.ui.lspFilterTextbox;

/**
 *
 * Client-side autocompletion for parenthesis/brackets/quotes FIXME: When
 * entering the first say '(' or '"', the text is completed correctly. But
 * sometimes the user will close the character himself to avoid having to use
 * the arrow to go after the closing character. If the user enters the closing
 * character, it should just move to the next one, ie remove the one that was
 * auto-added. For example, if I type '"', 'a', '"', I would expect the string
 * to be "a", now, it is "a""", with two trailing "". Same with parenthesis '(',
 * 'a', ')' should be (a), not (a)). See the behavior in Eclipse itself.
 *
 * @author Maxime Thibault
 */
public class FilterBoxLocalTextCompletion {

    private static final String LEFT_PARENTHESIS = "(";
    private static final String RIGHT_PARENTHESIS = ")";
    private static final String LEFT_SQUARE_BRACKET = "[";
    private static final String RIGHT_SQUARE_BRACKET = "]";
    private static final String LEFT_BRACKET = "{";
    private static final String RIGHT_BRACKET = "}";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String SINGLE_QUOTE = "'";
    private static final String SPACE = " ";

    /**
     * Autocomplete the string based on the cursorPos. This function suppose
     * that the last inserted character is at cursorPos-1
     *
     * @param str
     *            The string to complete
     * @param cursorPos
     *            The caret position
     * @return The completed string
     */
    public static String autocomplete(String str, Integer cursorPos) {

        boolean canComplete = canComplete(str, cursorPos);
        if (canComplete) {
            return complete(str, cursorPos);
        }
        return str;
    }

    /**
     * Check if the string can be completed based on the cursor's position We
     * assume that the token before the cursor's position is the token to be
     * completed
     *
     * @param str
     *            The string to autocomplete
     * @param cursorPos
     *            The caret position when the token has been inserted
     * @return boolean that indicated wheter the string can be completed or not
     */
    private static boolean canComplete(String str, Integer cursorPos) {
        boolean isValid = true;
        String charAtCursor = String.valueOf(str.charAt(cursorPos - 1));
        if (str.length() > cursorPos) {
            String nextChar = String.valueOf(str.charAt(cursorPos));

            boolean charAtCursorOk = charAtCursor.equals(LEFT_BRACKET) ||
                    charAtCursor.equals(LEFT_PARENTHESIS) ||
                    charAtCursor.equals(LEFT_SQUARE_BRACKET) ||
                    charAtCursor.equals(DOUBLE_QUOTE) ||
                    charAtCursor.equals(SINGLE_QUOTE) ||
                    charAtCursor.equals(SPACE);

            boolean rightOK = nextChar.equals(RIGHT_BRACKET) ||
                    nextChar.equals(RIGHT_PARENTHESIS) ||
                    nextChar.equals(RIGHT_SQUARE_BRACKET) ||
                    nextChar.equals(DOUBLE_QUOTE) ||
                    nextChar.equals(SINGLE_QUOTE) ||
                    nextChar.equals(SPACE);

            if (charAtCursor.equals(LEFT_BRACKET) ||
                    charAtCursor.equals(LEFT_PARENTHESIS) ||
                    charAtCursor.equals(LEFT_SQUARE_BRACKET)) {
                rightOK &= !nextChar.equals(DOUBLE_QUOTE) && !nextChar.equals(SINGLE_QUOTE);
            }

            isValid = rightOK && charAtCursorOk;
        }

        return isValid;
    }

    /**
     * Add the appropriate symbol at cursorPos to complete the autocompletion
     *
     * @param str
     *            The string to complete
     * @param cursorPos
     *            The caret position
     * @return The completed string
     */
    private static String complete(String str, Integer cursorPos) {
        String charAtCursor = String.valueOf(str.charAt(cursorPos - 1));
        String charToInsert;
        switch (charAtCursor) {
        case LEFT_PARENTHESIS:
            charToInsert = RIGHT_PARENTHESIS;
            break;
        case LEFT_BRACKET:
            charToInsert = RIGHT_BRACKET;
            break;
        case LEFT_SQUARE_BRACKET:
            charToInsert = RIGHT_SQUARE_BRACKET;
            break;
        case DOUBLE_QUOTE:
            charToInsert = DOUBLE_QUOTE;
            break;
        case SINGLE_QUOTE:
            charToInsert = SINGLE_QUOTE;
            break;
        default:
            // D'ont modify
            charToInsert = "";
            break;
        }

        return str.substring(0, cursorPos) + charToInsert + str.substring(cursorPos, str.length());
    }
}
