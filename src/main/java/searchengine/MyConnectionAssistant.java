package searchengine;

import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.config.Site;
import java.io.IOException;

@Getter
public class MyConnectionAssistant {

    public static Document getDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows; U; WindowsNT5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                .referrer("http://google.com")
                .get();
    }

    public static String getPathSite(String url, String siteUrl) {

        String urlNoWWW = url.replaceFirst("//www.", "//");
        String siteUrlNoWWW = siteUrl.replaceFirst("//www.", "//");
        if (!urlNoWWW.startsWith(siteUrlNoWWW)) {
            return "";
        }
        String pathSite = urlNoWWW.substring(siteUrlNoWWW.length());
        if (!pathSite.startsWith("/")) {
            pathSite = "/" + pathSite;
        }
        return pathSite;
    }

    public static String getSiteUrl(Site site) {
        try {
            Document document = getDocument(site.getUrl());
            return document.location().endsWith("/")
                    ? document.location().substring(0, document.location().length() - 1)
                    : document.location();
        } catch (IOException e) {
            return site.getUrl();
        }
    }
}
