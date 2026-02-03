## Telegram Channel Crawler (Новости СМЭВ+) — архитектура и API

### Архитектура
- **Основные компоненты**:
  - `NewsController` — REST-эндпоинт `GET /api/v1/news` (принимает диапазон дат).
  - `NewsService` — координатор: пагинация источника, дедупликация, фильтрация по датам, лимиты.
  - `RenderedTelegramClient` — headless-клиент (Playwright/Chromium) для рендера ленты Telegram-канала (`https://t.me/s/{channel}`), извлечения `id/url/title/publishedAt/text`. При необходимости открывает страницу поста для получения полного текста.
  - `RateLimiter` — простой лимитер (например, 1–2 запроса/секунду).
  - (Опционально) `InMemoryCache` с коротким TTL для повторных запросов того же диапазона.
- **Поток**:
  1. Контроллер валидирует `from`/`to` (ISO 8601, `from ≤ to`, ограничение по ширине окна, например ≤ 31 день).
  2. Сервис вызывает headless-клиент: рендерит ленту канала, скроллит для подгрузки старых постов, извлекает элементы.
  3. Фильтрация по датам и дедупликация по `id` выполняются на нашей стороне.
  4. Текст берётся из карточки поста в ленте; при обрезке — открывается страница поста по `url` и извлекается полный `text`. Возвращается массив новостей.

### Технологии и настройки
- Java 17, Spring Boot (WebFlux для неблокирующего I/O), Maven.
- Playwright (Chromium) для рендера ленты Telegram (и при необходимости детальных страниц постов).
- Таймауты: базовый `crawler.request-timeout-ms` (по умолчанию 5000), контроллер использует увеличенный таймаут.
- Заголовки: `User-Agent`, `Accept-Language`, `Accept`.
- Логирование ключевых событий и метрик (время, кол-во элементов, курсор).

## Формат API (OpenAPI 3.0.3)

```yaml
openapi: 3.0.3
info:
  title: Telegram News Crawler API
  version: 1.0.0
  description: REST API для поиска постов Telegram-канала https://t.me/s/smev_news по диапазону дат.
servers:
  - url: /api/v1
paths:
  /news:
    get:
      summary: Получить список новостей по диапазону дат
      operationId: listNews
      parameters:
        - name: from
          in: query
          required: true
          description: Начальная дата (включительно), формат ISO 8601 (YYYY-MM-DD)
          schema:
            type: string
            format: date
          example: "2026-01-01"
        - name: to
          in: query
          required: true
          description: Конечная дата (включительно), формат ISO 8601 (YYYY-MM-DD)
          schema:
            type: string
            format: date
          example: "2026-01-31"
        - name: limit
          in: query
          required: false
          description: Ограничение на кол-во записей в ответе (для защиты от слишком больших выборок)
          schema:
            type: integer
            minimum: 1
            maximum: 1000
            default: 500
        - name: cursor
          in: query
          required: false
          description: Курсор постраничной выгрузки (опционально; возвращается сервером в `nextCursor`)
          schema:
            type: string
      responses:
        "200":
          description: Успешный ответ
          headers:
            X-Request-Id:
              description: Идентификатор запроса для корреляции логов
              schema:
                type: string
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/NewsListResponse"
              examples:
                sample:
                  value:
                    items:
                      - id: "post-123456"
                        url: "https://t.me/smev_news/123456"
                        title: "Добрый день, уважаемые коллеги! Для Поставщиков доступны версии ПО «Витрина данных»..."
                        text: "Полный текст поста..."
                        publishedAt: "2026-01-15T10:32:00+03:00"
                      - id: "post-123457"
                        url: "https://t.me/smev_news/123457"
                        title: "История СМЭВ: завершён перевод всех обменов со СМЭВ2 на СМЭВ3 и СМЭВ4..."
                        text: "Полный текст поста..."
                        publishedAt: "2026-01-16T09:00:00+03:00"
                    count: 2
                    nextCursor: null
        "400":
          description: Ошибка валидации параметров
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Problem"
              examples:
                invalidRange:
                  value:
                    type: "https://httpstatuses.com/400"
                    title: "Bad Request"
                    status: 400
                    detail: "'from' должен быть <= 'to' и диапазон не должен превышать 31 день"
                invalidFormat:
                  value:
                    type: "https://httpstatuses.com/400"
                    title: "Bad Request"
                    status: 400
                    detail: "Неверный формат параметра запроса: ..."
        "429":
          description: Превышен лимит запросов или слишком широкий диапазон
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Problem"
        "502":
          description: Источник недоступен или вернул некорректный ответ
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Problem"
        "500":
          description: Внутренняя ошибка
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Problem"
components:
  schemas:
    NewsItem:
      type: object
      required: [id, url, title, text, publishedAt]
      properties:
        id:
          type: string
          description: Идентификатор поста (из источника или стабильно вычисленный хеш)
          example: "post-123456"
        url:
          type: string
          format: uri
          description: Прямая ссылка на пост
          example: "https://t.me/smev_news/123456"
        title:
          type: string
          description: Заголовок поста — первая строка текста или укороченный префикс
          example: "История СМЭВ: завершён перевод всех обменов..."
        text:
          type: string
          description: Полный текст поста (из карточки или детальной страницы)
          example: "Полный текст ..."
        publishedAt:
          type: string
          format: date-time
          description: Дата и время публикации поста (ISO 8601, с таймзоной если доступна)
          example: "2026-01-15T10:32:00+03:00"
    NewsListResponse:
      type: object
      required: [items, count]
      properties:
        items:
          type: array
          items:
            $ref: "#/components/schemas/NewsItem"
        count:
          type: integer
          description: Количество элементов в текущем ответе
          example: 2
        nextCursor:
          type: string
          nullable: true
          description: Курсор для продолжения выгрузки (если данных больше)
          example: null
    Problem:
      type: object
      required: [title, status]
      properties:
        type:
          type: string
          format: uri
          description: URI типа проблемы
        title:
          type: string
          description: Краткое описание проблемы
        status:
          type: integer
          description: HTTP статус
        detail:
          type: string
          description: Детали ошибки
        instance:
          type: string
          description: Идентификатор/трассировка конкретного случая
```

## Пример запроса

```bash
curl -G "http://localhost:8080/api/v1/news" \
  --data-urlencode "from=2026-01-01" \
  --data-urlencode "to=2026-01-31" \
  --data-urlencode "limit=200"
```

## Настройки (application.yml)

Ключи `crawler.*`:

- `crawler.rate-limit-rps` — RPS к источнику (по умолчанию 2).
- `crawler.request-timeout-ms` — базовый таймаут сетевых операций и ожиданий рендера (по умолчанию 5000).
- `crawler.retry.max-attempts` — попытки при сбоях.
- `crawler.retry.backoff-ms` — бэкофф между повторами.
- `crawler.cache.enabled` — вкл/выкл in-memory кэш ответов.
- `crawler.cache.ttl-ms` — TTL кэша (мс).
- `crawler.source.base-url` — базовый URL ленты канала (`https://t.me/s/smev_news`).
- `crawler.source.user-agent` — заголовок `User-Agent`.
- `crawler.source.accept-language` — заголовок `Accept-Language`.
- `crawler.max-range-days` — максимальная ширина окна дат (по умолчанию 31).
- `crawler.default-limit` — лимит по умолчанию (по умолчанию 500).
- `crawler.max-limit` — предельный лимит (по умолчанию 1000).

## Логика парсинга

1) Лента:
- Открываем `crawler.source.base-url` (формат `https://t.me/s/{channel}`) в headless Chromium (Playwright).
- Ждём появления элементов постов `.tgme_widget_message`.
- Скроллируем вниз, пока не наберём нужное количество элементов.
- Для каждого поста извлекаем:
  - `url` — из ссылки даты `.tgme_widget_message_date a` (канонический вид: `https://t.me/{channel}/{messageId}`).
  - `publishedAt` — из `time[datetime]` внутри `.tgme_widget_message_date`, либо парсим текст даты.
  - `text` — агрегированный текст из `.tgme_widget_message_text` с сохранением переносов строк.
  - `title` — первая непустая строка `text` (обрезаем до 120–160 символов).
  - `id` — по хвосту `messageId` с префиксом `post-`.

2) Пагинация:
- Курсор формата `o:<offset>` — смещение от начала общего списка.
- `nextCursor` вычисляется исходя из достигнутого лимита.

3) Полный текст:
- По умолчанию используем `text` из ленты `.tgme_widget_message_text`.
- Если встречается обрезка/признак «ещё», переходим по `url` поста и извлекаем полный `text`.
- Ошибки не блокируют общий ответ — `text` может быть пустым.

4) Фильтрация и дедупликация:
- Фильтр по датам `[from..to]` и дедупликация по `id` — на нашей стороне.

## Ошибки и таймауты
- 400 — валидация (`from > to`, ширина окна, некорректная дата/формат).
- 429 — превышение лимитов (если применимо).
- 502 — недоступность источника.
- 500 — внутренняя ошибка.
- Таймаут контроллера увеличен относительно `crawler.request-timeout-ms` для учёта рендера и прокрутки ленты.

## Примеры

CLI:
```bash
./scripts/run.sh
./scripts/query.sh 2026-01-01 2026-01-31 200
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Минимальная заготовка Maven-зависимостей

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
  </dependency>
  <dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-log4j2</artifactId>
    <optional>true</optional>
  </dependency>
  <dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
  </dependency>
  <dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
    <version>2.6.0</version>
  </dependency>
  <!-- Для генерации клиента/спеки можно добавить плагин openapi -->
</dependencies>
```

### Конфигурация и валидация
- **Конфиги**: `crawler.rateLimit.rps`, `crawler.requestTimeoutMs`, `crawler.retry.maxAttempts`, `crawler.retry.backoffMs`, `crawler.cache.ttlMs`.
- **Валидация**: ограничить ширину диапазона (например, ≤ 31 день) и `from ≤ to`.
- **Обработка сбоев**: 502 при недоступности источника, 429 при превышении лимитов.

