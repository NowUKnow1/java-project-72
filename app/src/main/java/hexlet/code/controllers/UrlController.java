package hexlet.code.controllers;

import groovy.util.logging.Log4j2;
import hexlet.code.domain.Url;
import hexlet.code.domain.UrlCheck;
import hexlet.code.domain.query.QUrl;
import hexlet.code.domain.query.QUrlCheck;
import io.javalin.http.Handler;
import io.javalin.http.NotFoundResponse;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

import kong.unirest.UnirestException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static io.ebeaninternal.api.CoreLog.log;

@Log4j2
public final class UrlController {

    public static Handler displayUrl = ctx -> {

        long id = ctx.pathParamAsClass("id", Long.class).getOrDefault(null);

        Url url = new QUrl()
                .id.equalTo(id)
                .findOne();

        if (url == null) {
            throw new NotFoundResponse();
        }


        List<UrlCheck> checks = url.getChecks();

        ctx.attribute("checks", checks);
        ctx.attribute("url", url);

        ctx.render("check.html");
    };


    public static Handler checkUrl = ctx -> {

        long id = ctx.pathParamAsClass("id", Long.class).getOrDefault(null);

        Url url = new QUrl()
                .id.equalTo(id)
                .findOne();

        if (url == null) {
            throw new NotFoundResponse();
        }

        try {
            HttpResponse<String> responseGet = Unirest
                    .post(url.getName())
                    .asString();

            String content = responseGet.getBody();

            Document body = Jsoup.parse(content);

            String h1 = body.selectFirst("h1") != null
                    ? Objects.requireNonNull(body.selectFirst("h1")).text()
                    : null;
            String description = body.selectFirst("meta[name=description]") != null
                    ? Objects.requireNonNull(body.selectFirst("meta[name=description]")).attr("content")
                    : null;


            UrlCheck urlCheck = new UrlCheck(responseGet.getStatus(), body.title(), h1, description, url);

            urlCheck.save();

            ctx.sessionAttribute("flash", "???????????????? ?????????????? ??????????????????");
            ctx.sessionAttribute("flash-type", "success");
        } catch (UnirestException e) {
            ctx.sessionAttribute("flash", "???? ?????????????? ?????????????????? ????????????????");
            ctx.sessionAttribute("flash-type", "danger");
        } catch (Exception e) {
            ctx.sessionAttribute("flash", "???? ?????????????? ?????????????????? ????????????????");
            ctx.sessionAttribute("flash-type", "danger");
        }

        ctx.redirect("/urls/" + url.getId());
    };


    public static Handler createUrl = ctx -> {
        String normalizedUrl = ctx.formParam("url");

        try {
            log.debug("?????????????? ?????????????????????????? ???????????????????? URL {}", normalizedUrl);
            URL inputUrl = new URL(Objects.requireNonNull(normalizedUrl));

            normalizedUrl = inputUrl.getProtocol() + "://" + inputUrl.getAuthority();

            log.debug("???????????????? ?????? ???????????? URL {} ?????? ?????? ?? ????", normalizedUrl);

            Url databaseUrl = new QUrl()
                    .name.equalTo(normalizedUrl)
                    .findOne();

            if (Objects.nonNull(databaseUrl)) {
                log.debug("?????????? URL {} ?????? ???????????????????? ?? ????", normalizedUrl);
                ctx.sessionAttribute("flash", "???????????? ?????? ????????????????????");
                ctx.sessionAttribute("flash-type", "info");
                ctx.redirect("/urls");
                return;
            }

            Url url = new Url(normalizedUrl);
            url.save();

            ctx.sessionAttribute("flash", "???????????????? ?????????????? ??????????????????");
            ctx.sessionAttribute("flash-type", "success");
            log.debug("URL {} ???????????????? ?? DB", normalizedUrl);

        } catch (MalformedURLException e) {
            log.debug("???? ?????????????? ?????????????????????????? URL");
            ctx.sessionAttribute("flash", "???????????????????????? URL");
            ctx.sessionAttribute("flash-type", "danger");
            ctx.redirect("/");
            return;
        }

        ctx.redirect("/urls");
    };



    public static Handler listUrls = ctx -> {

        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
        int rowsPerPage = 10;
        int offset = (page - 1) * rowsPerPage;

        QUrl url = QUrl.alias();
        QUrlCheck urlCheck = QUrlCheck.alias();

        List<Url> urls = new QUrl()
                .select(url.id, url.name)
                .setFirstRow(offset)
                .setMaxRows(rowsPerPage)
                .orderBy()
                .id.asc()
                .checks.fetch(urlCheck.statusCode, urlCheck.createdAt)
                .orderBy().checks.createdAt.desc()
                .findPagedList().getList();

        ctx.attribute("urls", urls);
        ctx.attribute("page", page);
        ctx.render("show.html");
    };

}
