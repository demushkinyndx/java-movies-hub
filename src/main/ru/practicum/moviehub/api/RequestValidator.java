package ru.practicum.moviehub.api;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class RequestValidator {
    private static final int MIN_YEAR = 1888;

    public static List<String> validate(String title, Integer year) {
        List<String> details = new ArrayList<>();
        int maxYear = Year.now().getValue() + 1;

        if (title == null || title.isBlank()) {
            details.add("Название не должно быть пустым");
        } else if (title.length() > 100) {
            details.add("Название не должно быть длиннее 100 символов");
        }

        if (year == null) {
            details.add("Год должен быть между " + MIN_YEAR + " и " + maxYear);
        } else if (year < MIN_YEAR || year > maxYear) {
            details.add("Год должен быть между " + MIN_YEAR + " и " + maxYear);
        }

        return details;
    }
}
