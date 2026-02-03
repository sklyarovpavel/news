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

1.1) Поднять локальную LLM (Ollama) и скачать модель:
```bash
docker compose up -d ollama ollama-pull
# либо дождаться авто-скачивания при первом запросе к API
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

## LLM (классификация новостей)
- В `docker-compose.yml` добавлен сервис `ollama` (порт 11434). По умолчанию используется модель `llama3.2:3b`.
- Конфигурация:
  - `llm.ollama.baseUrl` (или `OLLAMA_BASE_URL`), по умолчанию `http://localhost:11434`
  - `llm.ollama.model` (или `OLLAMA_MODEL`), по умолчанию `llama3.2:3b`

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

