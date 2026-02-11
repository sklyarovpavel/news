# News App (Spring Boot + Vaadin + Postgres)

Три экранные формы (CRUD):
- Пользователи
- Ресурсы
- Сообщения

Навигационная панель расположена слева (Vaadin AppLayout).

## Стек
- Java 17
- Spring Boot 3 + Spring Data JPA
- Vaadin 24
- PostgreSQL (через docker-compose только для БД)
- Maven

## Запуск

1) Поднять БД (только база в Docker):
```bash
docker compose up -d db
```

2) Запустить приложение локально (НЕ через docker-compose):
```bash
mvn spring-boot:run
```

Приложение откроется по адресу `http://localhost:8080`.

## Конфигурация БД
По умолчанию параметры берутся из `application.properties`:
- URL: `jdbc:postgresql://localhost:5432/newsdb`
- USER: `news`
- PASSWORD: `news`

Можно переопределить через переменные окружения:
- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`

## LLM (GigaChat, классификация/сводка новостей)
- Используется Spring AI GigaChat. Нужен API-ключ.
- Переменные окружения:
  - `GIGACHAT_API_KEY` — ключ доступа (обязательно)
  - `GIGACHAT_SCOPE` — по умолчанию `gigachat_api_pers`
  - `GIGACHAT_TEMPERATURE` — по умолчанию `0.2`

### Тест REST API
POST `http://localhost:8080/api/llm/classify`
```bash
curl -s -X POST http://localhost:8080/api/llm/classify \
  -H 'Content-Type: application/json' \
  -d '{"text":"Apple представила новые процессоры для ноутбуков..."}'
```
Ответ:
```json
{"suitable": true, "confidence": 0.82}
```

## Структура экранов
- Пользователи: логин, email (уникальные), дата создания
- Ресурсы: название, URL, описание, дата создания
- Сообщения: текст, автор (пользователь), ресурс (опционально), дата создания

Схема создаётся автоматически (Hibernate `ddl-auto=update`).

