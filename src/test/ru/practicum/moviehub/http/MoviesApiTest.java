package ru.practicum.moviehub.http;

import com.google.gson.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static final Gson gson = new Gson();
    private static MoviesServer server;
    private static MoviesStore moviesStore;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        moviesStore = new MoviesStore();
        server = new MoviesServer(8080, moviesStore);
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        server.start();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @BeforeEach
    void beforeEach() {
        moviesStore.clear();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = callGetMethod("/movies");

        assertEquals(HttpStatusCode.OK, resp.statusCode());
        assertJsonContentType(resp);
        assertEquals(List.of(), ListOfMoviesTypeToken.parse(resp.body(), gson), "Список фильмов должен быть пустым");
    }

    @Test
    void getMovies_whenHasMovies_returnsAllMovies() throws Exception {
        postAllMovies();

        HttpResponse<String> resp = callGetMethod("/movies");

        assertEquals(HttpStatusCode.OK, resp.statusCode());
        assertJsonContentType(resp);
        assertMoviesEqual(ListOfMoviesTypeToken.TEST_MOVIES, ListOfMoviesTypeToken.parse(resp.body(), gson));
    }

    @Test
    void getMovies_withYearFilter_returnsMatchingMovies() throws Exception {
        postAllMovies();

        HttpResponse<String> resp = callGetMethod("/movies?year=1999");

        assertEquals(HttpStatusCode.OK, resp.statusCode());
        assertMoviesEqual(List.of(ListOfMoviesTypeToken.TEST_MOVIES.getFirst()),
                ListOfMoviesTypeToken.parse(resp.body(), gson));
    }

    @Test
    void getMovies_withUnknownQueryParam_returnsAllMovies() throws Exception {
        postAllMovies();

        HttpResponse<String> resp = callGetMethod("/movies?genre=drama");

        assertEquals(HttpStatusCode.OK, resp.statusCode());
        assertMoviesEqual(ListOfMoviesTypeToken.TEST_MOVIES, ListOfMoviesTypeToken.parse(resp.body(), gson));
    }

    @Test
    void getMovies_withInvalidYear_returns400() throws Exception {
        HttpResponse<String> resp = callGetMethod("/movies?year=abc");

        assertEquals(HttpStatusCode.BAD_REQUEST, resp.statusCode());
        assertJsonContentType(resp);
        assertEquals("Некорректный параметр запроса — 'year'", parseJsonBody(resp.body()).get("error").getAsString());
    }

    @Test
    void getMovies_withEmptyYear_returns400() throws Exception {
        HttpResponse<String> resp = callGetMethod("/movies?year=");

        assertEquals(HttpStatusCode.BAD_REQUEST, resp.statusCode());
        assertEquals("Некорректный параметр запроса — 'year'", parseJsonBody(resp.body()).get("error").getAsString());
    }

    @Test
    void postMovie_withValidDataAndSpaces_returns201AndMovie() throws Exception {
        HttpResponse<String> resp = callPostMethod(Map.of("title", " Матрица ", "year", 1999));

        assertEquals(HttpStatusCode.CREATED, resp.statusCode());
        assertJsonContentType(resp);

        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertEquals(1, movie.id(), "Id полученного фильма не соответствует Id сохраненного");
        assertEquals("Матрица", movie.title(), "Название полученного фильма не соответствует сохраненному");
        assertEquals(1999, movie.year(), "Год выпуска полученного фильма не соответствует сохраненному");
    }


    @Test
    void postMovie_withValidData2Movies_returns201AndMovies() throws Exception {
        callPostMethod(Map.of("title", "Матрица", "year", 1999));
        callPostMethod(Map.of("title", "Матрица 2", "year", 2003));
        HttpResponse<String> resp = callGetMethod("/movies");
        assertEquals(2, ListOfMoviesTypeToken.parse(resp.body(), gson).size(), "Кол-во полученных фильмов не соответствует кол-ву сохраненных");
    }

    @Test
    void postMovie_withEmptyTitle_returns422() throws Exception {
        HttpResponse<String> resp = callPostMethod(Map.of("title", "   ", "year", 1975));

        assertEquals(HttpStatusCode.UNPROCESSABLE_CONTENT, resp.statusCode());
        JsonObject jsonObject = parseJsonBody(resp.body());
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "От сервера не получено корректное сообщение об ошибке");
        assertTrue(containsDetailsString(jsonObject, "Название не должно быть пустым"), "Детали ошибки от сервера не содержат нужный ответ");
    }

    @Test
    void postMovie_withTooLongTitle_returns422() throws Exception {
        String title = "a".repeat(101);
        HttpResponse<String> resp = callPostMethod(Map.of("title", title, "year", 1975));

        assertEquals(HttpStatusCode.UNPROCESSABLE_CONTENT, resp.statusCode());
        JsonObject jsonObject = parseJsonBody(resp.body());
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "От сервера не получено корректное сообщение об ошибке");
        assertTrue(containsDetailsString(jsonObject, "Название не должно быть длиннее 100 символов"), "Детали ошибки от сервера не содержат нужный ответ");
    }

    @Test
    void postMovie_withInvalidYear_returns422() throws Exception {
        int invalidYear = Year.now().getValue() + 2;
        HttpResponse<String> resp = callPostMethod(Map.of("title", "Фильм", "year", invalidYear));
        assertEquals(HttpStatusCode.UNPROCESSABLE_CONTENT, resp.statusCode());
        JsonObject jsonObject = parseJsonBody(resp.body());
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "От сервера не получено корректное сообщение об ошибке");
        assertTrue(containsDetailsString(jsonObject, "Год должен быть между 1888 и " + (Year.now().getValue() + 1)), "Детали ошибки от сервера не содержат нужный ответ");
    }

    @Test
    void postMovie_withMissingYear_returns422() throws Exception {
        HttpResponse<String> resp = postRawJson("{\"title\":\"Фильм\"}");

        assertEquals(HttpStatusCode.UNPROCESSABLE_CONTENT, resp.statusCode());
        JsonObject jsonObject = parseJsonBody(resp.body());
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "От сервера не получено корректное сообщение об ошибке");
        assertTrue(containsDetailsString(jsonObject, "Год должен быть между 1888 и " + (Year.now().getValue() + 1)), "Детали ошибки от сервера не содержат нужный ответ");
    }

    @Test
    void postMovie_withInvalidJson_returns422() throws Exception {
        HttpResponse<String> resp = postRawJson("{invalid");

        assertEquals(HttpStatusCode.UNPROCESSABLE_CONTENT, resp.statusCode());
        assertTrue(resp.body().contains("некорректный JSON"));

        JsonObject jsonObject = parseJsonBody(resp.body());
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "От сервера не получено корректное сообщение об ошибке");
        assertTrue(containsDetailsString(jsonObject, "некорректный JSON"), "Детали ошибки от сервера не содержат нужный ответ");
    }

    @Test
    void postMovie_withWrongContentType_returns415() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"Фильм\",\"year\":2000}"))
                .build();

        HttpResponse<String> resp = send(req);

        assertEquals(HttpStatusCode.UNSUPPORTED_MEDIA_TYPE, resp.statusCode());

        JsonObject jsonObject = parseJsonBody(resp.body());
        assertEquals("Неподдерживаемый тип содержимого", jsonObject.get("error").getAsString(), "От сервера не получено корректное сообщение об ошибке");
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        postAllMovies();

        HttpResponse<String> resp = callGetMethod("/movies/2");

        assertEquals(HttpStatusCode.OK, resp.statusCode());
        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertEquals(2, movie.id());
        assertEquals("Начало", movie.title());
        assertEquals(2010, movie.year());
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = callGetMethod("/movies/999");

        assertEquals(HttpStatusCode.NOT_FOUND, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void getMovieById_withInvalidId_returns400() throws Exception {
        HttpResponse<String> resp = callGetMethod("/movies/abc");

        assertEquals(HttpStatusCode.BAD_REQUEST, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный ID"));
    }

    @Test
    void getMovieById_withExtraPath_returns404() throws Exception {
        HttpResponse<String> resp = callGetMethod("/movies/1/extra");

        assertEquals(HttpStatusCode.NOT_FOUND, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void deleteMovieById_whenExists_returns204() throws Exception {
        postAllMovies();

        HttpResponse<String> resp = delete("/movies/3");

        assertEquals(HttpStatusCode.NO_CONTENT, resp.statusCode());
        assertEquals("", resp.body());

        HttpResponse<String> getResp = callGetMethod("/movies");
        List<Movie> movies = ListOfMoviesTypeToken.parse(getResp.body(), gson);
        assertEquals(5, movies.size());
        assertMoviesEqual(List.of(
                ListOfMoviesTypeToken.TEST_MOVIES.get(0),
                ListOfMoviesTypeToken.TEST_MOVIES.get(1),
                ListOfMoviesTypeToken.TEST_MOVIES.get(3),
                ListOfMoviesTypeToken.TEST_MOVIES.get(4),
                ListOfMoviesTypeToken.TEST_MOVIES.get(5)
        ), movies);
    }

    @Test
    void deleteMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = delete("/movies/999");

        assertEquals(HttpStatusCode.NOT_FOUND, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void unsupportedMethod_onCollection_returns405() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = send(req);

        assertEquals(HttpStatusCode.METHOD_NOT_ALLOWED, resp.statusCode());
        assertTrue(resp.body().contains("Метод не поддерживается"));
    }

    @Test
    void unsupportedMethod_onMovieById_returns405() throws Exception {
        postAllMovies();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = send(req);

        assertEquals(HttpStatusCode.METHOD_NOT_ALLOWED, resp.statusCode());
        assertTrue(resp.body().contains("Метод не поддерживается"));
    }

    private void postAllMovies() throws Exception {
        for (Movie movie : ListOfMoviesTypeToken.TEST_MOVIES) {
            callPostMethod(Map.of("title", movie.title(), "year", movie.year()));
        }
    }

    private HttpResponse<String> callGetMethod(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .GET()
                .build();
        return send(req);
    }

    private HttpResponse<String> callPostMethod(Map<String, Object> body) throws Exception {
        return postRawJson(gson.toJson(body));
    }

    private HttpResponse<String> postRawJson(String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(req);
    }

    private HttpResponse<String> delete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .DELETE()
                .build();
        return send(req);
    }

    private HttpResponse<String> send(HttpRequest req) throws Exception {
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertJsonContentType(HttpResponse<String> resp) {
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);
    }

    private JsonObject parseJsonBody(String jsonBody) {
        return JsonParser.parseString(jsonBody).getAsJsonObject();
    }

    private void assertMoviesEqual(List<Movie> expected, List<Movie> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Movie exp = expected.get(i);
            Movie act = actual.get(i);
            assertEquals(exp.id(), act.id(), "Id полученного фильма не соответствует сохраненному");
            assertEquals(exp.title(), act.title(), "Название полученного фильма не соответствует сохраненному");
            assertEquals(exp.year(), act.year(), "Год выпуска полученного фильма не соответствует сохраненному");
        }
    }

    private boolean containsDetailsString(JsonObject jsonObject, String stringPattern) {
        JsonArray detailsArray = jsonObject.getAsJsonArray("details");
        for (int i = 0; i < detailsArray.size(); i++) {
            String item = detailsArray.get(i).getAsString();
            if (item.equals(stringPattern)) {
                return true;
            }
        }
        return false;
    }
}
