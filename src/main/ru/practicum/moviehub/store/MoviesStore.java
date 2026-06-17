package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MoviesStore {
    private final Map<Long, Movie> movies = new HashMap<>();
    private long nextId = 1;

    public void clear() {
        movies.clear();
        nextId = 1;
    }

    public Movie add(String title, int year) {
        Movie movie = new Movie(nextId++, title, year);
        movies.put(movie.id(), movie);
        return movie;
    }

    public Optional<Movie> getById(long id) {
        return Optional.ofNullable(movies.get(id));
    }

    public List<Movie> getAll() {
        List<Movie> result = new ArrayList<>(movies.values());
        result.sort(Comparator.comparingLong(Movie::id));
        return result;
    }

    public boolean delete(long id) {
        return movies.remove(id) != null;
    }

    public List<Movie> findByYear(int year) {
        List<Movie> result = new ArrayList<>();
        for (Movie movie : getAll()) {
            if (movie.year() == year) {
                result.add(movie);
            }
        }
        return result;
    }
}
