package searchengine;

import org.jsoup.nodes.Document;
import searchengine.config.Site;
import searchengine.config.SitesList;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class Validator {

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
            Document document = MyConnector.getDocument(site.getUrl());
            return document.location().endsWith("/")
                    ? document.location().substring(0, document.location().length() - 1)
                    : document.location();
        } catch (IOException e) {
            return site.getUrl();
        }
    }

    public static String getSiteUrl(String pageUrl) {
        try {
            URL url = new URL(pageUrl);
            return url.getProtocol() + "://" + url.getHost();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean urlIsLocatedConfig(String url, SitesList sitesList) {
        for (Site site : sitesList.getSites()) {
            if (url.startsWith(site.getUrl())) {
                return true;
            }
        }
        return false;
    }
}
