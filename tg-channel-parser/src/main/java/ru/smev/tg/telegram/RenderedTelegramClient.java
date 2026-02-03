package ru.smev.tg.telegram;

import reactor.core.publisher.Mono;

import java.util.List;

public interface RenderedTelegramClient {
    Mono<List<RawPost>> fetchPosts(int offset, int count);
}

