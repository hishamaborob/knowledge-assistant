package com.kbassistant.domain.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class FixedSizeChunkerTest {

    private final FixedSizeChunker chunker = new FixedSizeChunker(5, 2);

    // chunkSize=5, overlap=2, stride=3

    @Test
    void nullInput_returnsEmptyList() {
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void blankInput_returnsEmptyList() {
        assertThat(chunker.chunk("   ")).isEmpty();
        assertThat(chunker.chunk("")).isEmpty();
    }

    @Test
    void textShorterThanChunkSize_returnsSingleChunk() {
        List<String> result = chunker.chunk("one two three");
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("one two three");
    }

    @Test
    void textExactlyChunkSize_returnsSingleChunk() {
        // 5 words exactly
        List<String> result = chunker.chunk("a b c d e");
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("a b c d e");
    }

    @Test
    void textOverChunkSize_createsMultipleChunks() {
        // 8 words: "a b c d e f g h"
        // chunk 0: words 0-4 → "a b c d e"
        // chunk 1: words 3-7 → "d e f g h"  (stride=3, start at 3)
        List<String> result = chunker.chunk("a b c d e f g h");
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo("a b c d e");
        assertThat(result.get(1)).isEqualTo("d e f g h");
    }

    @Test
    void overlapIsCorrect_lastChunkEndsAtTextEnd() {
        // 11 words, chunkSize=5, stride=3
        // chunk 0: 0-4
        // chunk 1: 3-7
        // chunk 2: 6-10
        List<String> result = chunker.chunk("w1 w2 w3 w4 w5 w6 w7 w8 w9 w10 w11");
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo("w1 w2 w3 w4 w5");
        assertThat(result.get(1)).isEqualTo("w4 w5 w6 w7 w8");
        assertThat(result.get(2)).isEqualTo("w7 w8 w9 w10 w11");
    }

    @Test
    void lastChunkIsShorterThanChunkSize_stillIncluded() {
        // 6 words: chunk0=0-4, chunk1=3-5 (only 3 words, shorter than chunkSize)
        List<String> result = chunker.chunk("a b c d e f");
        assertThat(result).hasSize(2);
        assertThat(result.get(1)).isEqualTo("d e f");
    }

    @Test
    void multipleWhitespace_treatedAsWordBoundaries() {
        List<String> result = chunker.chunk("a  b   c    d");
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("a b c d");
    }

    @Test
    void constructor_invalidChunkSize_throws() {
        assertThatThrownBy(() -> new FixedSizeChunker(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_overlapGteChunkSize_throws() {
        assertThatThrownBy(() -> new FixedSizeChunker(5, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void realWorldConfig_512chunkSize_64overlap() {
        FixedSizeChunker realChunker = new FixedSizeChunker(512, 64);
        // 700-word text should produce 2 chunks
        String text = "word ".repeat(700).trim();
        List<String> chunks = realChunker.chunk(text);
        assertThat(chunks).hasSize(2);
        // First chunk: 512 words
        assertThat(chunks.get(0).split("\\s+")).hasSize(512);
        // Second chunk starts with overlap: begins at word 448 (512-64), ends at 700
        assertThat(chunks.get(1).split("\\s+")).hasSize(700 - 448);
    }
}
