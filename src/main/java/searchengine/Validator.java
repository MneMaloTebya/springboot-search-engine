package searchengine;

import org.jsoup.nodes.Document;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.LemmaEntity;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static LemmaEntity findLemmaInList(List<LemmaEntity> lemmaList, String lemma) {
        return lemmaList
                .stream()
                .filter(l -> l.getLemma().equals(lemma))
                .findFirst()
                .orElse(null);
    }



    public static List<String> getRightSentences(List<String> sentences, Set<String> lemmas) {
        List<String> rightSentences = new ArrayList<>();
        for (String sentence : sentences) {
            for (String lemma : lemmas) {
                if(sentence.contains(lemma)) {
                    rightSentences.add(sentence);
                }
            }
        }
        return rightSentences.stream().limit(2).collect(Collectors.toList());
    }

    public static List<String> splitTextIntoSentences(String text) {
        String[] replacedText = text.split("\\.");
        return new ArrayList<>(Arrays.asList(replacedText));
    }

    public static int [] findLemmaIndex(String lemma, String text) {
        int[] indexes = new int[2];
        if (text.contains(lemma)) {
            indexes[0] = text.indexOf(lemma);
            indexes[1] = indexes[0] + lemma.length();
        }
        return indexes;
    }

    public static String getFormattedSentence (int[] indexes, String sentence, String lemma) {
        int from = indexes[0];
        int to = indexes[1];
        String firstPart = sentence.substring(0, from);
        String secondPart = "<b>".concat(lemma).concat("<b>");
        String finalPart = sentence.substring(to);
        return firstPart.concat(secondPart).concat(finalPart);
    }


}
