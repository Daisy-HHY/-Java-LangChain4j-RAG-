package com.kgqa.util;

/**
 * 统一整理模型回答的换行和段落，避免长答案挤成一整段。
 */
public final class AnswerFormatter {

    private AnswerFormatter() {
    }

    public static String format(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }

        String formatted = answer.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        if (formatted.contains("\n") || formatted.length() < 140) {
            return formatted;
        }

        return insertParagraphBreaks(formatted);
    }

    private static String insertParagraphBreaks(String text) {
        StringBuilder sb = new StringBuilder();
        int charsSinceBreak = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            sb.append(ch);
            charsSinceBreak++;

            if (charsSinceBreak >= 70 && isSentenceBoundary(ch)) {
                sb.append("\n\n");
                charsSinceBreak = 0;
            }
        }

        return sb.toString().trim();
    }

    private static boolean isSentenceBoundary(char ch) {
        return ch == '。' || ch == '！' || ch == '？' || ch == ';' || ch == '；';
    }
}
