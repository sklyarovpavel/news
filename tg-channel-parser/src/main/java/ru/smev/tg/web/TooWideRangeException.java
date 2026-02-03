package ru.smev.tg.web;

public class TooWideRangeException extends RuntimeException {
    public TooWideRangeException(String message) {
        super(message);
    }
    public static TooWideRangeException of(String message) {
        return new TooWideRangeException(message);
    }
}

