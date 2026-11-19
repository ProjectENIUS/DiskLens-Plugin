# DiskLens: автотест вида в чате Minecraft

Бот Mineflayer (offline-режим) заходит на временный Paper 1.21.11 (порт **25800**), получает OP через консоль и выполняет `/disklens`, `/disklens plugins/ 1 5`, `/disklens files 5`, `/disklens status`, `/disklens ./ -r`; второй бот с локалью `ru_RU` запускает `/disklens`. Чат читается как JSON-компоненты, а не как текст.

## Запуск
```bash
cd release/screenshots/visual-test
npm install            # один раз (mineflayer из npmjs.com)
node test.js           # прогон + сравнение с baseline.json
node test.js --update-baseline   # принять новый вид как эталон
node test.js --keep-server       # не удалять папку server/ после прогона
```
Нужны: Node 18+, JDK 21 (`JAVA=...` если не `C:\Program Files\Zulu\zulu-21`), собранный `build/libs/DiskLens-2.0.0.jar` (`PLUGIN_JAR=...`), `itest/cache/paper-1.21.11.jar` (только читается, `PAPER_JAR=...`), Edge или Chrome для PNG. Версия клиента: `MC_VERSION` (по умолчанию 1.21.11). Код возврата 1, если хоть одна проверка упала.

## Что проверяется (24 проверки + 5 сравнений с baseline)
- строки дерева: заголовок, глифы `├─ └─`, папки, «ещё N»;
- цвета сегментов: папки aqua, размеры red (≥50%) / yellow (5–20%) / green (<5%), ветки dark_gray, проценты gray, заголовок files gold, пути white;
- `clickEvent run_command` на папках, `[↑ up]`, `[Rescan]`, `[Report to file]`; hover-подсказка на папке;
- `status`, повторный скан `-r`; action bar при скане (если скан слишком быстрый на демо-данных, выводится WARN, не ошибка);
- русская локаль клиента: «корень сервера», ГБ/МБ, кнопки `[Пересканировать]`/`[Отчёт в файл]`;
- сравнение со `baseline.json` (меняющиеся числа времени «N s ago» нормализуются).

## Результаты
`out/*.png` — отрисовка полученных компонентов (чат-стиль, как в `../html`), `out/captured-components.json` — сырые данные, `out/results.json` — итоги. Демо-сервер создаётся в `server/` (sparse-файлы) и удаляется по окончании.
