-- Обновление URL на внутренние имена сервисов в Docker
update resource_items set url = 'http://fedresurs-crawler:8081/api/v1/news', updated_at = now()
where name = 'Fedresurs';
insert into resource_items (name, url, description, created_at, updated_at, last_processed_at, polling_enabled, polling_interval_minutes)
select 'Fedresurs', 'http://fedresurs-crawler:8081/api/v1/news', 'Новости из fedresurs-crawler', now(), now(), now() - interval '5 days', true, 60
where not exists (select 1 from resource_items where name = 'Fedresurs');

update resource_items set url = 'http://gosuslugi-rss:8082/api/v1/news', updated_at = now()
where name = 'Gosuslugi RSS';
insert into resource_items (name, url, description, created_at, updated_at, last_processed_at, polling_enabled, polling_interval_minutes)
select 'Gosuslugi RSS', 'http://gosuslugi-rss:8082/api/v1/news', 'Новости из gosuslugi-rss', now(), now(), now() - interval '5 days', true, 60
where not exists (select 1 from resource_items where name = 'Gosuslugi RSS');

update resource_items set url = 'http://tg-channel-parser:8083/api/v1/news', updated_at = now()
where name = 'Telegram Channels';
insert into resource_items (name, url, description, created_at, updated_at, last_processed_at, polling_enabled, polling_interval_minutes)
select 'Telegram Channels', 'http://tg-channel-parser:8083/api/v1/news', 'Новости из tg-channel-parser', now(), now(), now() - interval '5 days', true, 60
where not exists (select 1 from resource_items where name = 'Telegram Channels');

