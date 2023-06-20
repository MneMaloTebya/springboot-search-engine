package searchengine;

import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.config.Site;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

@Getter
public class PageValidator {

    public static Document getDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows; U; WindowsNT5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                .referrer("http://google.com")
                .get();
    }

    public static String getPathSite(String url, String siteUrl) {

        String urlWWW = url.replaceFirst("//www.", "//");
        String siteUrlWWW = siteUrl.replaceFirst("//www.", "//");
        if (!urlWWW.startsWith(siteUrlWWW)) {
            return "";
        }
        String pathSite = urlWWW.substring(siteUrlWWW.length());
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

    public static String getPathFromUrl(String pageUrl) {
        String path = "";
        try {
            URL url = new URL(pageUrl);
            if (pageUrl.startsWith("/")) {
                path = pageUrl;
            } else if (pageUrl.startsWith("http")) {
                path = url.getPath();
            }

        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return path;
    }

    public static String getHostFromUrl(String pageUrl) {
        try {
            URL url = new URL(pageUrl);
            return url.getProtocol() + "://" + url.getHost() + "/";
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
