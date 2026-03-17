package com.fscrawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SpringBootApplication
public class FscrawlerApplication {

    private static final Logger log = LoggerFactory.getLogger(FscrawlerApplication.class);
    private static final String SEARCHCOUNTRY_FRAGMENT = "fstravel.com/searchtour/";
    private static final String SEARCHHOTEL_FRAGMENT = "fstravel.com/searchhotel/";
    private static final String EXCURSION_TOUR = "fstravel.com/excursiontour/";

    private static final String HOTEL_FRAGMENT = "fstravel.com/hotel/";
    private static final String NO_RESULTS_TEXT = "Мы не нашли вариантов";
    private static final String STILL_SEARCHING_TEXT = "Ищем подходящие предложения";
    private static final String QUICK_SELECTION_FAIL = "Поиск не дал результатов";
    private static final String ZERO_OFFERS = "найдено 0 предложений";
    private static final String MAIN_PAGE_SLIDER_FINDER = "buttonLink&quot;:&quot;";

    public static void main(String[] args) {
        log.info("Запуск приложения fscrawler");
        SpringApplication.run(FscrawlerApplication.class, args);
    }

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        log.debug("Создание RestTemplate с таймаутами");
        return builder
                .setConnectTimeout(Duration.ofSeconds(60))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
    }

    @Bean
    CommandLineRunner crawl(RestTemplate restTemplate) {
        log.debug("Инициализация CommandLineRunner для обхода ссылок");
        return args -> {
            String[] strings = fetchBody(restTemplate, "https://www.fstravel.com").split(MAIN_PAGE_SLIDER_FINDER);
            List<String> sliderLinks = new ArrayList<>();
            List<String> sliderLinksSearch = new ArrayList<>();
            for (int i = 2; i < strings.length; i++) {
                String[] extractedSliderShowToRaw = Arrays.stream(strings[i].split("slideShowTo&quot;:")).limit(2).toArray(String[]::new);
                String extractedSliderShowToRawClean = extractedSliderShowToRaw[1].substring(0, extractedSliderShowToRaw[1].indexOf(','));
                ZonedDateTime showTillBarrier = ZonedDateTime.now(ZoneId.of("Europe/Moscow"));
                if (!extractedSliderShowToRawClean.equals("null")) {
                    Long showTill = Long.parseLong(extractedSliderShowToRawClean);
                    Instant instant = Instant.ofEpochSecond(showTill);
                    showTillBarrier = instant.atZone(ZoneId.of("Europe/Moscow"));
                }

                if (extractedSliderShowToRawClean.equals("null") || showTillBarrier.isAfter(ZonedDateTime.now(ZoneId.of("Europe/Moscow")))) {

                    String extractedSliderLink = strings[i].substring(0, strings[i].indexOf('&')).replace("\\", "");
                    StringBuilder finalExtractedSliderLink = new StringBuilder();
                    if (extractedSliderLink.startsWith("https://fstravel.com")) {
                        sliderLinksSearch.add(extractedSliderLink);
                    } else if (extractedSliderLink.startsWith("https://")) {
                    } else {
                        if (extractedSliderLink.startsWith("/")) {
                            finalExtractedSliderLink.append("https://fstravel.com").append(extractedSliderLink);
                        } else {
                            finalExtractedSliderLink.append("https://fstravel.com/").append(extractedSliderLink);
                        }
                        sliderLinks.add(finalExtractedSliderLink.toString());
                    }
                }
            }
            for (String s : sliderLinksSearch) {
                log.info("Ссылка на поиск тура в слайдере -- {}", s);
            }
            for (String s : sliderLinks) {
                log.info("Обработали ссылку -- {}", s);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss");
            String fileName = "reports/" + LocalDateTime.now(ZoneId.of("Europe/Moscow")).format(formatter) + ".txt";
            try {
                Path path = Paths.get(fileName);
                List<String> rows = new ArrayList<>();
                log.info("Найдено {} ссылок c поиском в слайдере", sliderLinksSearch.size());
                for (String s : sliderLinksSearch) {
                    log.info("Обход страницы поиска из слайдера: {}", s);
                    rows.add("Обход страницы поиска из слайдера: " + s);
                    String html = fetchBody(restTemplate, s);
                    if (html == null) {
                        log.error("Не удалось загрузить страницу поиска из слайдера: {}", s);
                        rows.add("ПРОБЛЕМА! Не удалось загрузить страницу поиска из слайдера: " + s + "\n");
                        continue;
                    }
                    if (checkLink(restTemplate, s, s, rows)) {
                        log.info("Страница {} проанализирована. Успех!", s);
                        rows.add("Страница проанализирована. Успех!\n");
                    } else {
                        log.info("ОШИБКА! Страница поиска из слайдера: {}", s);
                        rows.add("ОШИБКА! СТРАНИЦА ПОИСКА ИЗ СЛАЙДЕРА: " + s + "\n");
                    }
                }

                for (String s : sliderLinks) {
                    log.info("Начало обхода страницы: {}", s);
                    rows.add("Начало обхода страницы: " + s);
                    String html = fetchBody(restTemplate, s);
                    if (html == null) {
                        log.error("Не удалось загрузить страницу: {}", s);
                        rows.add("ПРОБЛЕМА! Не удалось загрузить страницу: " + s + "\n");
                        continue;
                    } else if (html.contains(QUICK_SELECTION_FAIL)) {
                        log.error("На странице {} ОШИБКА ОТОБРАЖЕНИЯ ПРОСТОЙ ПОДБОРКИ", s);
                        rows.add("ОШИБКА ОТОБРАЖЕНИЯ ПРОСТОЙ ПОДБОРКИ НА СТРАНИЦЕ " + s);
                        continue;
                    }

                    Set<String> links = extractTargetLinks(html, s);
                    log.info("Найдено {} целевых ссылок", links.size());
                    int ok = 0;
                    int no = 0;
                    for (String link : links) {
                        if (checkLink(restTemplate, link, s, rows)) {
                            ok++;
                        } else {
                            no++;
                        }
                    }
                    log.info("Обход ссылок завершен. На странице {} успешно открыто: {}. Неуспешно: {}", s, ok, no);
                    rows.add("Обход ссылок завершен. На странице " + s + " успешно открыто: " + ok + ". Неуспешно: " + no + "\n");

                }
                String rowFinal = rows.stream()
                        .collect(Collectors.joining("\n"));
                Files.writeString(
                        path,
                        rowFinal + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException e) {
                throw new RuntimeException("Ошибка при записи CSV: " + fileName, e);
            }
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
                .filter(href -> href != null && (href.contains(SEARCHCOUNTRY_FRAGMENT) || href.contains(SEARCHHOTEL_FRAGMENT) || href.contains(HOTEL_FRAGMENT) || href.contains(EXCURSION_TOUR)))
                .forEach(result::add);

        log.debug("После фильтрации получено {} уникальных ссылок", result.size());
        return result;
    }

    private boolean checkLink(RestTemplate restTemplate, String link, String page, List<String> rows) {
        log.debug("Проверка ссылки: {}", link);
        String body = fetchBody(restTemplate, link);
        try {
            if (body.contains(ZERO_OFFERS)) {
                log.error("По ссылке нет предложений. Фильтр 'Найдено предложений': {}", link);
                rows.add("ОШИБКА: 0 ПРЕДЛОЖЕНИЙ ПО ССЫЛКЕ " + link);
                return false;
            } else if (body.contains(NO_RESULTS_TEXT)) {
                log.error("По ссылке нет выдачи: {}", link);
                rows.add("ОШИБКА: НА СТРАНИЦЕ " + page + " НЕ НАШЛИ ВАРИАНТОВ " + link);
                return false;
            } else if (body.contains(STILL_SEARCHING_TEXT)) {
                log.warn("Долгий поиск!: {}. Перепроверяем", link);
                String test = fetchBodyWithSelenium(link);
                if (test.contains(ZERO_OFFERS)) {
                    log.error("По ссылке нет предложений. Фильтр 'Найдено предложений': {}", link);
                    rows.add("ОШИБКА: 0 ПРЕДЛОЖЕНИЙ ПО ССЫЛКЕ " + link);
                    return false;
                } else if (test.contains(NO_RESULTS_TEXT)) {
                    log.error("SEL ОШИБКА: Проверка выдала 0 результатов по ссылке {} со страницы {}", link, page);
                    rows.add("ОШИБКА: 0 ПРЕДЛОЖЕНИЙ ПО ССЫЛКЕ " + link);
                    return false;
                } else if (test.contains("swiper-slide-active")) {
                    log.info("SEL УСПЕХ: Проверка нашла результаты выдачи по ссылке {} со страницы {}", link, page);
                    return true;
                } else {
                    log.warn("SEL ОК: Проверка не подтвердила пустую выдачу по ссылке {} со страницы {}", link, page);
                    rows.add("ПРОВЕРКА: за 15 секунд НЕ ПОДТВЕРДИЛАСЬ пустая выдача по ссылке " + link);
                    return true;
                }
            } else {
                log.info("ОК {}", link);
                return true;
            }
        } catch (NullPointerException e) {
            log.error("Непредвиденная ошибка при переходе со страницы {} по ссылке {}", page, link);
            rows.add("НЕПРЕДВИДЕННАЯ ОШИБКА при переходе по ссылке " + link);
            return false;
        }
    }

    private String fetchBodyWithSelenium(String url) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = new ChromeDriver(options);
        driver.get(url);

        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));

            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("section.not-result")
            ));

            return driver.getPageSource();
        } catch (TimeoutException e) {
            driver.get(url);

            return driver.getPageSource();
        } finally {
            driver.quit();
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
