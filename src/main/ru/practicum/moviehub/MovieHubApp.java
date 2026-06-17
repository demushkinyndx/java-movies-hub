package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;
import ru.practicum.moviehub.store.MoviesStore;

public class MovieHubApp {
    public static void main(String[] args) {
        final MoviesServer server = new MoviesServer(8080, new MoviesStore());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}