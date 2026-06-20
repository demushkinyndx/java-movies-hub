package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.api.PostRequest;
import ru.practicum.moviehub.api.RequestValidator;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

class MoviesHandler extends BaseHttpHandler {

    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .create();
    private final MoviesStore moviesStore;

    public MoviesHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            handleCollection(ex, method);

        } catch (Exception e) {
            sendJson(ex, HttpStatusCode.INTERNAL_SERVER_ERROR, gson.toJson(new ErrorResponse("Внутренняя ошибка сервера")));
        }
    }

    private void handleCollection(HttpExchange ex, String method) throws IOException {
        switch (method) {
            case "GET" -> handleGetMovies(ex);
            case "POST" -> handlePostMovie(ex);
            default -> sendJson(ex, HttpStatusCode.METHOD_NOT_ALLOWED, gson.toJson(new ErrorResponse("Метод не поддерживается")));
        }
    }

    private void handleGetMovies(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();
        if (query == null || query.isBlank()) {
            sendJson(ex, HttpStatusCode.OK, gson.toJson(moviesStore.getAll()));
            return;
        }

        String yearParam = getQueryParam(query, "year");
        if (yearParam == null) {
            sendJson(ex, HttpStatusCode.OK, gson.toJson(moviesStore.getAll()));
            return;
        }

        if (yearParam.isEmpty() || !yearParam.matches("\\d+")) {
            sendJson(ex, HttpStatusCode.BAD_REQUEST, gson.toJson(new ErrorResponse("Некорректный параметр запроса — 'year'")));
            return;
        }

        int year = Integer.parseInt(yearParam);
        sendJson(ex, HttpStatusCode.OK, gson.toJson(moviesStore.findByYear(year)));
    }

    private String getQueryParam(String query, String name) {
        if (query == null || name == null) {
            return null;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length > 0 && pair[0].equals(name)) {
                return pair.length > 1 ? pair[1] : "";
            }
        }
        return null;
    }

    private void handlePostMovie(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (!isJsonContentType(contentType)) {
            sendJson(ex, HttpStatusCode.UNSUPPORTED_MEDIA_TYPE, gson.toJson(new ErrorResponse("Неподдерживаемый тип содержимого")));
            return;
        }

        String body = readBody(ex);
        PostRequest request;
        try {
            request = gson.fromJson(body, PostRequest.class);
        } catch (JsonSyntaxException e) {
            sendJson(ex, HttpStatusCode.UNPROCESSABLE_CONTENT, gson.toJson(new ErrorResponse("Ошибка валидации",
                    List.of("некорректный JSON"))));
            return;
        }

        if (request == null) {
            sendJson(ex, HttpStatusCode.UNPROCESSABLE_CONTENT, gson.toJson(new ErrorResponse("Ошибка валидации",
                    List.of("некорректный JSON"))));
            return;
        }

        List<String> details = RequestValidator.validate(request.getTitle(), request.getYear());
        if (!details.isEmpty()) {
            sendJson(ex, HttpStatusCode.UNPROCESSABLE_CONTENT, gson.toJson(new ErrorResponse("Ошибка валидации", details)));
            return;
        }

        Movie movie = moviesStore.add(request.getTitle().trim(), request.getYear());
        sendJson(ex, HttpStatusCode.CREATED, gson.toJson(movie));
    }

    private boolean isJsonContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase().split(";")[0].trim();
        return normalized.equals("application/json");
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
