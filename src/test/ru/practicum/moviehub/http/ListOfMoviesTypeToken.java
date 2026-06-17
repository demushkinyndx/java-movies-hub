package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ru.practicum.moviehub.model.Movie;

import java.util.List;

public class ListOfMoviesTypeToken extends TypeToken<List<Movie>> {

    public static final List<Movie> TEST_MOVIES = List.of(
            new Movie(1, "Матрица", 1999),
            new Movie(2, "Начало", 2010),
            new Movie(3, "Брат", 1997),
            new Movie(4, "Свадебная ваза", 1975),
            new Movie(5, "Властелин колец: Охота на Голлума", 2027),
            new Movie(6, "Сцена в саду Раундхэй", 1888)
    );

    public static List<Movie> parse(String json, Gson gson) {
        return gson.fromJson(json, new ListOfMoviesTypeToken().getType());
    }
}
