package ru.smev.tg.web;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
    public static BadRequestException of(String message) {
        return new BadRequestException(message);
    }
}

