# Скриншоты DiskLens для галерей

Все данные — демо (общие имена, файлы разрежённые, на диске почти не занимают места). Консольные картинки — настоящий вывод Paper 1.21.11 + DiskLens 2.0.0 из `logs/latest.log` (пути заменены на `/home/container`).

**Важно (честная подпись):** клиента Minecraft не было, поэтому «чатовые» картинки (01, 03, 05, 06) — это **рендер реального вывода плагина** (текст из консоли, цвета и кнопки как в `Renderer`/`ChatOut`) на нейтральном размытом фоне; это не живые скриншоты игры. Подпись «Rendered from real plugin output» нанесена на самих картинках. В 01 и 05 показан тот же скан с глубиной 1 (вложенные строки убраны), в 02 вывод обрезан.

| Файл | Что показывает | Куда |
|---|---|---|
| 01-tree-chat.png | дерево в чате, цвета по долям | первая картинка на всех площадках |
| 02-console-panel.png | консоль/панель хостинга | Spigot, Hangar, Modrinth |
| 03-largest-files.png | `disklens files` | Modrinth, CurseForge |
| 04-report.png | `export` + файл отчёта | Hangar, Spigot |
| 05-russian.png | русский язык | SpigotMC (русская ветка), Modrinth |
| 06-drill-down-chat.png | клики, подсказка, кнопки | Modrinth, CurseForge, Hangar |
| 07-console-plugins.png | `plugins/ 1 5`, `status` | запасная |

Остальное: `captions.md` — подписи EN/RU; `text/` — захваченный вывод (`console-en.log`, `console-ru.log`, `report-sample.txt`); `html/` — исходники страниц и `build.py` (генерирует их из `text/`; PNG снимались headless Edge, 1400 px).
