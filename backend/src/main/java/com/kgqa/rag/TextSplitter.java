package com.kgqa.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;

    public List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // 按段落分割
        String[] paragraphs = text.split("\\n\\s*\\n");

        StringBuilder currentChunk = new StringBuilder();
        int currentLength = 0;

        for (String paragraph : paragraphs) {
            // 跳过空段落
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int paragraphLength = trimmed.length();

            if (currentLength + paragraphLength > chunkSize && currentLength > 0) {
                chunks.add(currentChunk.toString().trim());

                // 处理重叠
                String overlapText = currentChunk.toString();
                currentChunk = new StringBuilder();

                if (overlapText.length() > overlap) {
                    currentChunk.append(overlapText.substring(overlapText.length() - overlap));
                    currentLength = overlap;
                } else {
                    currentLength = 0;
                }
            }

            currentChunk.append(trimmed).append("\n\n");
            currentLength += paragraphLength + 2;
        }

        if (currentChunk.length() > 0 && !currentChunk.toString().trim().isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}
