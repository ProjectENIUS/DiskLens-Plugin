# SpigotMC: поля формы «Post Resource»

Статус полей: **[V]** проверено на сайте, **[A]** предположение (страницу правил получить не удалось: wiki-страницы 404/403, форма требует логина).

| Поле | Значение | Статус |
|---|---|---|
| Категория | **Spigot → Tools and Utilities** (категория существует, видна в каталоге; 19 812 ресурсов) | [V] |
| Название (title) | `DiskLens - Disk Usage Tree` (26 симв.). Альтернатива: `DiskLens - What Takes Up Server Disk Space` (40 симв.). Без версий, звёздочек, эмодзи и слов «best/#1». В каталоге встречаются названия до ~70 симв., но форма лимит хранит у себя | [A] правила: коротко, по делу, без рекламы; лимит длины форма покажет |
| Tag line | `Disk usage tree of your server folders: sizes, top-N, clickable in chat, plain text for console/RCON` (≈100 симв.). Если форма не пропустит, короткая: `Find out which server folders eat your disk space` (50 симв.) | [A] лимит ≈ 100 симв., не подтверждён |
| Версия (version string) | `2.0.0` | [V] из build.gradle.kts / plugin.yml |
| Native Minecraft version | **1.16.5** (по указанию родительской сессии; если в форме только «1.16», выберите её) | [V] родительская сессия |
| Tested versions (галочки) | По `summary-final.md` PASS: **1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.4, 1.20.6, 1.21.4, 1.21.11** (Paper/Purpur/Spigot; Folia 1.20.6, 1.21.11; Folia 1.19.4 — всё, кроме RCON). Ставьте галочки только на эти. Версии 1.20.1–1.20.2, 1.21.0–1.21.3, 1.21.5–1.21.10 не тестировались напрямую; если в форме есть такие пункты, не отмечайте. 26.x не тестировался | [V] summary-final.md |
| Языки (Languages) | English, Russian | [V] lang/en.yml, lang/ru.yml |
| Source code link | `<ПОДСТАВИТЬ ссылку на репозиторий, например GitHub>` (оставьте пустым, если репозиторий закрыт) | placeholder |
| Contributors | `<ПОДСТАВИТЬ ники, через запятую>` | placeholder |
| Donation link | *(пусто)* | по заданию |
| Support / discussion | включить Discussion-тред ресурса (в форме опция есть; название может отличаться) | [A] |
| Тип | Free (бесплатный) | по заданию |
| Иконка | PNG/JPG, квадратная, например 512×512 (не больше нескольких сотен КБ) | [A] размеры форма проверит |
| Файл | `build/libs/DiskLens-2.0.0.jar` загрузить как файл (не внешняя ссылка) | [A] правило SpigotMC |

## Тесты
Блок «Supported servers and versions» в `description.bbcode` заполнен по `itest/results/summary-final.md`. Оговорки, уже включённые в текст: Folia 1.19.4 без RCON (ограничение самой Folia), PlugManX проверен только на 1.20.6+, 26.x не тестировалась, клики/hover в игре автоматикой не покрыты (визуальный тест ещё идёт).

## Теги / ключевые слова (если форма спросит)
disk, disk usage, storage, folder size, tools, admin, utility, folia, paper, purpur
