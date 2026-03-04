package com.fscrawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(FscrawlerApplication.class);

    private static final String SOURCE_URL = "https://fstravel.com/actions/ranneye-bronirovaniye-leto-2026";
    private static final String TARGET_FRAGMENT = "fstravel.com/searchtour/country";
    private static final String NO_RESULTS_TEXT = "Мы не нашли вариантов";

    public static void main(String[] args) {
        log.info("Запуск приложения fscrawler");
        SpringApplication.run(FscrawlerApplication.class, args);
    }

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        log.debug("Создание RestTemplate с таймаутами");
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Bean
    CommandLineRunner crawl(RestTemplate restTemplate) {
        log.debug("Инициализация CommandLineRunner для обхода ссылок");
        return args -> {
            log.info("Начало обхода страницы: {}", SOURCE_URL);
            String html = fetchBody(restTemplate, SOURCE_URL);
            if (html == null) {
                log.error("Не удалось загрузить страницу: {}", SOURCE_URL);
                System.err.println("Не удалось загрузить страницу: " + SOURCE_URL);
                return;
            }

            Set<String> links = extractTargetLinks(html, SOURCE_URL);
            log.info("Найдено {} целевых ссылок", links.size());
            for (String link : links) {
                checkLink(restTemplate, link);
            }
            log.info("Обход ссылок завершен");
        };
    }

    private Set<String> extractTargetLinks(String html, String baseUrl) {
        log.debug("Извлечение целевых ссылок из страницы: {}", baseUrl);
        Document document = Jsoup.parse(html, baseUrl);
        Set<String> result = new LinkedHashSet<>();

        document.select("a[href]")
                .stream()
                .map(element -> element.attr("abs:href"))
                .filter(href -> href != null && !href.isBlank())
                .map(this::normalize)
                .filter(href -> href != null && href.contains(TARGET_FRAGMENT))
                .forEach(result::add);

        log.debug("После фильтрации получено {} уникальных ссылок", result.size());
        return result;
    }

    private void checkLink(RestTemplate restTemplate, String link) {
        log.debug("Проверка ссылки: {}", link);
        String body = fetchBody(restTemplate, link);
        if (body == null) {
            log.warn("Ссылка недоступна: {}", link);
            System.out.println(link + " ОШИБКА: НЕ ОТКРЫВАЕТСЯ");
            return;
        }

        if (body.contains(NO_RESULTS_TEXT)) {
            log.info("По ссылке нет выдачи: {}", link);
            System.out.println(link + " ОШИБКА: НЕТ ВЫДАЧИ");
        } else {
            log.info("Ссылка успешно открыта с выдачей: {}", link);
            System.out.println(link + " ОК");
        }
    }

    private String fetchBody(RestTemplate restTemplate, String url) {
        log.debug("HTTP GET: {}", url);
        try {
            String response = restTemplate.getForObject(url, String.class);
            log.debug("HTTP GET успешен: {}", url);
            return response;
        } catch (RestClientException ex) {
            log.warn("Ошибка при запросе URL {}: {}", url, ex.getMessage());
            return null;
        }
    }

    private String normalize(String url) {
        log.trace("Нормализация URL: {}", url);
        try {
            URI uri = new URI(url);
            URI normalized = new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
            String normalizedUrl = normalized.toString();
            log.trace("Нормализованный URL: {}", normalizedUrl);
            return normalizedUrl;
        } catch (URISyntaxException e) {
            log.warn("Некорректный URL, возвращаю исходный: {}", url);
            return url;
        }
    }
}
