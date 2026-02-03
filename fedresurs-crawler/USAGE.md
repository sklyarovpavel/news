## Запуск и примеры

Требуется: Java 17+, Maven.

### Запуск приложения

```bash
./scripts/run.sh
```

По умолчанию активирован демо-клиент (без реальных запросов к fedresurs.ru), чтобы можно было сразу протестировать API.

В текущей версии используется только реальный HTTP‑клиент к `https://fedresurs.ru/news`.

### Пример запроса

```bash
./scripts/query.sh 2026-01-01 2026-01-31 200
```

Аналогично:

```bash
curl -G "http://localhost:8080/api/v1/news" \
  --data-urlencode "from=2026-01-01" \
  --data-urlencode "to=2026-01-31" \
  --data-urlencode "limit=200"
```

### Тесты

```bash
mvn -q -DskipITs test
```

