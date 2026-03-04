package com.fscrawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@SpringBootApplication
public class FscrawlerApplication {

    private static final String SOURCE_URL = "https://fstravel.com/actions/ranneye-bronirovaniye-leto-2026";
    private static final String TARGET_FRAGMENT = "fstravel.com/searchtour/country";
    private static final String NO_RESULTS_TEXT = "Мы не нашли вариантов";

    public static void main(String[] args) {
        SpringApplication.run(FscrawlerApplication.class, args);
    }

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Bean
    CommandLineRunner crawl(RestTemplate restTemplate) {
        return args -> {
            String html = fetchBody(restTemplate, SOURCE_URL);
            if (html == null) {
                System.err.println("Не удалось загрузить страницу: " + SOURCE_URL);
                return;
            }

            Set<String> links = extractTargetLinks(html, SOURCE_URL);
            for (String link : links) {
                checkLink(restTemplate, link);
            }
        };
    }

    private Set<String> extractTargetLinks(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        Set<String> result = new LinkedHashSet<>();

        document.select("a[href]")
                .stream()
                .map(element -> element.attr("abs:href"))
                .filter(href -> href != null && !href.isBlank())
                .map(this::normalize)
                .filter(href -> href != null && href.contains(TARGET_FRAGMENT))
                .forEach(result::add);

        return result;
    }

    private void checkLink(RestTemplate restTemplate, String link) {
        String body = fetchBody(restTemplate, link);
        if (body == null) {
            System.out.println(link + " ОШИБКА: НЕ ОТКРЫВАЕТСЯ");
            return;
        }

        if (body.contains(NO_RESULTS_TEXT)) {
            System.out.println(link + " ОШИБКА: НЕТ ВЫДАЧИ");
        } else {
            System.out.println(link + " ОК");
        }
    }

    private String fetchBody(RestTemplate restTemplate, String url) {
        try {
            return restTemplate.getForObject(url, String.class);
        } catch (RestClientException ex) {
            return null;
        }
    }

    private String normalize(String url) {
        try {
            URI uri = new URI(url);
            URI normalized = new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
            return normalized.toString();
        } catch (URISyntaxException e) {
            return url;
        }
    }
}
