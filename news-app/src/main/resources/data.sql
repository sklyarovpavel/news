-- Инициализация ресурсных эндпоинтов для соседних сервисов
-- Выполняется один раз; повторные вставки игнорируются условием NOT EXISTS по name

-- Fedresurs Crawler
insert into resource_items (name, url, description, created_at, updated_at, last_processed_at, polling_enabled, polling_interval_minutes)
select 'Fedresurs', 'http://localhost:8081/api/v1/news', 'Новости из fedresurs-crawler', now(), now(), now() - interval '10 days', true, 60
where not exists (select 1 from resource_items where name = 'Fedresurs');

-- Gosuslugi RSS
insert into resource_items (name, url, description, created_at, updated_at, last_processed_at, polling_enabled, polling_interval_minutes)
select 'Gosuslugi RSS', 'http://localhost:8082/api/v1/news', 'Новости из gosuslugi-rss', now(), now(), now() - interval '10 days', true, 60
where not exists (select 1 from resource_items where name = 'Gosuslugi RSS');

-- Telegram Parser
insert into resource_items (name, url, description, created_at, updated_at, last_processed_at, polling_enabled, polling_interval_minutes)
select 'Telegram Channels', 'http://localhost:8083/api/v1/news', 'Новости из tg-channel-parser', now(), now(), now() - interval '10 days', true, 60
where not exists (select 1 from resource_items where name = 'Telegram Channels');

