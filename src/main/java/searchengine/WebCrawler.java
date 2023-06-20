package searchengine;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.services.IndexServiceImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

public class WebCrawler extends RecursiveTask<PageEntity> {

    private PageEntity pageEntity;
    private SiteEntity siteEntity;
    private IndexServiceImpl indexServiceImpl;
    private List<PageEntity> pageEntityList;

    public WebCrawler(PageEntity pageEntity, List<PageEntity> pageEntityList, IndexServiceImpl indexServiceImpl) {
        this.pageEntity = pageEntity;
        this.siteEntity = pageEntity.getSite();
        this.pageEntityList = pageEntityList;
        this.indexServiceImpl = indexServiceImpl;
    }

    @Override
    protected PageEntity compute() {

        if (siteEntity.getStatusType() == StatusType.FAILED) {
            return pageEntity;
        }

        Document document;
        try {
            document = PageValidator.getDocument(siteEntity.getUrl().concat(pageEntity.getPath()));
        } catch (IOException ex) {
            pageEntity.setCode(404);
            return pageEntity;
        }

        pageEntity.setCode(document.connection().response().statusCode());
        pageEntity.setContent(document.outerHtml());

        List<WebCrawler> webCrawlerList = getUrlChildList(document);

        for(WebCrawler webCrawler : webCrawlerList) {
            webCrawler.join();
        }

        synchronized (pageEntityList) {
            List<PageEntity> pagesToInsert = pageEntityList.stream().filter(p -> p.getCode() > 0).toList();
            if (pagesToInsert.size() > 500) {
                indexServiceImpl.insetAllData(pagesToInsert, siteEntity);
                pageEntityList.removeAll(pagesToInsert);
            }
        }
        return pageEntity;
    }

    private List<WebCrawler> getUrlChildList(Document document) {
        List<WebCrawler> siteList = new ArrayList<>();
        Elements elements = document.select("a[href~=^[^#?]+$]");
        for (Element element : elements) {
            String urlChild = element.attr("abs:href");

//            String pathSiteChild = PageValidator.getPathSite(urlChild, siteEntity.getUrl());
            String pathSiteChild = PageValidator.getPathFromUrl(urlChild);
            if (pathSiteChild.isBlank()) {
                continue;
            }

            PageEntity pageEntityChild;
            synchronized (pageEntityList) {
                if (indexServiceImpl.getPageRepository().existsByPathAndSite(pathSiteChild, siteEntity)
                        || pageEntityList.stream().anyMatch(p -> p.getPath().equals(pathSiteChild))) {
                    continue;
                }
                pageEntityChild = new PageEntity(siteEntity, pathSiteChild, 0, "");
                pageEntityList.add(pageEntityChild);
            }

            WebCrawler webCrawler = new WebCrawler(pageEntityChild, pageEntityList, indexServiceImpl);
            webCrawler.fork();
            siteList.add(webCrawler);
        }
        return siteList;
    }
}
