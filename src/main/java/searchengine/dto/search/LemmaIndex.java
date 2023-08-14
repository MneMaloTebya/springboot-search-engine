package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Objects;

@Data
@AllArgsConstructor
public class LemmaIndex {
    private String lemma;
    private int from;
    private int to;

    public static LemmaIndex findLemmaIndex(String lemma, String text) {
        int from = text.indexOf(lemma);
        int to = from + lemma.length();
        return new LemmaIndex(lemma, from, to);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LemmaIndex that = (LemmaIndex) o;
        return from == that.from && to == that.to;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }
}
