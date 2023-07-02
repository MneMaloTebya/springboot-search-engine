package searchengine;

import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.IOException;

@Getter
public class MyConnector {

    public static Document getDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows; U; WindowsNT5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                .referrer("http://google.com")
                .get();
    }
}
