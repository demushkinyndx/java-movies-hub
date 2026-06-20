package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.util.Optional;

class MovieByIdHandler extends BaseHttpHandler {

    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .create();
    private static final String MOVIES_PATH = "/movies";
    private final MoviesStore moviesStore;

    public MovieByIdHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String subPath = path.substring(MOVIES_PATH.length());

            handleByMovieId(ex, method, subPath);

        } catch (Exception e) {
            sendJson(ex, HttpStatusCode.INTERNAL_SERVER_ERROR, gson.toJson(new ErrorResponse("Внутренняя ошибка сервера")));
        }
    }

    private void handleByMovieId(HttpExchange ex, String method, String subPath) throws IOException {
        if (!subPath.startsWith("/")) {
            sendJson(ex, HttpStatusCode.NOT_FOUND, gson.toJson(new ErrorResponse("Фильм не найден")));
            return;
        }

        String idPart = subPath.substring(1);
        if (idPart.contains("/")) {
            sendJson(ex, HttpStatusCode.NOT_FOUND, gson.toJson(new ErrorResponse("Фильм не найден")));
            return;
        }

        Optional<Long> id = parseId(idPart);
        if (id.isEmpty()) {
            sendJson(ex, HttpStatusCode.BAD_REQUEST, gson.toJson(new ErrorResponse("Некорректный ID")));
            return;
        }

        switch (method) {
            case "GET" -> handleGetMovieById(ex, id.get());
            case "DELETE" -> handleDeleteMovie(ex, id.get());
            default -> sendJson(ex, HttpStatusCode.METHOD_NOT_ALLOWED, gson.toJson(new ErrorResponse("Метод не поддерживается")));
        }
    }

    private void handleGetMovieById(HttpExchange ex, long id) throws IOException {
        Optional<Movie> movie = moviesStore.getById(id);
        if (movie.isEmpty()) {
            sendJson(ex, HttpStatusCode.NOT_FOUND, gson.toJson(new ErrorResponse("Фильм не найден")));
            return;
        }
        sendJson(ex, HttpStatusCode.OK, gson.toJson(movie.get()));
    }

    private void handleDeleteMovie(HttpExchange ex, long id) throws IOException {
        if (!moviesStore.delete(id)) {
            sendJson(ex, HttpStatusCode.NOT_FOUND, gson.toJson(new ErrorResponse("Фильм не найден")));
            return;
        }
        sendNoContent(ex);
    }

    private Optional<Long> parseId(String idPart) {
        if (!idPart.matches("\\d+")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(idPart));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
