# Bridge 0.5.0

Сохраняет прежние маршруты REST/MCP, D1-таблицу nc_bridge и три секрета. Данные и ключи не сбрасывать. Поддерживает старые экспорты/патчи; для Assistant 0.5 требует свежий request_id и SHA-256. Не подменяет хеш. .nplug нужно обновить отдельно.

Новые возможности:
- POST /api/device/result: отчёты preview/applied/rejected/error с patch_id и event_id; только ключ устройства.
- POST /api/patch/status {id}, MCP patch_status: история результата конкретного патча.
- bridge_status: request_id, patch_result, bridge_version.
- Защита от повторного ID, конфликтов очереди и устаревших квитанций; сохранение истории в D1.
- Очередь: queued. «Применён» появляется только после отчёта устройства. Старый плагин не умеет подтверждать применение.

Cloudflare: npm ci → npm run check → npm test → npm run deploy. В подключённом репозитории Build npm run check, Deploy npm run deploy, Path cloudflare-bridge, ветка newcatroid-cloudflare. Имя в wrangler.jsonc должно совпадать с существующим Worker. В этой сборке оно purple-resonance-01d7. DB/секреты сохраняются. npm run deploy -- --dry-run проверяет пакет без публикации.

Архив также содержит termux/server.py и тесты. Python: остановить старый процесс, заменить файл, запустить с прежними аргументами и каталогом данных. ~/.newcatroid-bridge сохраняется. Не запускайте оба сервера на одном каталоге одновременно.

Тесты: 35 проверок Worker через SQLite-адаптер D1; 15 Python/HTTP/MCP тестов. Настоящий Cloudflare D1 и телефон Android в этих тестах не запускались. Обновление GitHub запускает обычный Cloudflare Build; успешный live deploy нужно подтвердить в его журнале.
