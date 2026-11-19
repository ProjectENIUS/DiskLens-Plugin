# Публикация DiskLens 2.0.0

Скрипт `publish.py` (Python 3.10+, только стандартная библиотека) загружает релиз на Modrinth, CurseForge, Hangar и GitHub.
**По умолчанию это dry-run**: ничего не отправляется, скрипт печатает запрос (токены скрыты) и проверяет, что файлы существуют, а версии сопоставляются. Реальная отправка только с флагом `--yes`.

Токены читаются **только из переменных окружения**, нигде не сохраняются и не печатаются.

## 1. Сначала создайте проекты вручную (веб-интерфейс)

Modrinth, CurseForge и Hangar принимают версии только в уже существующий проект. Тексты для форм лежат в `release/<сайт>/` (`description.md`, `metadata.md`, `checklist.md`).

1. Modrinth: создайте проект (тип Plugin), slug `disklens`.
2. CurseForge: создайте проект (Bukkit Plugins); числовой ID виден на странице обзора проекта.
3. Hangar: создайте проект `DiskLens`.
4. GitHub: репозиторий (см. раздел «GitHub» ниже).
5. SpigotMC: вручную, API загрузки нет.

## 2. Заполните `release.json`

| Поле | Что вписать |
|---|---|
| `modrinth.project` | slug (`disklens`) или ID проекта |
| `curseforge.project_id` | числовой ID проекта |
| `hangar.slug` | slug проекта на Hangar |
| `github.repo` | `владелец/репозиторий` |
| `modrinth.update_body` | `true`, если нужно заодно обновить описание проекта из `release/modrinth/description.md` |
| `game_versions` | список версий (1.16.5–1.21.11, без 26.x) |
| `hangar.hangar_versions` | `null` = брать `game_versions`; если Hangar не знает какие-то версии (например старые 1.16–1.18), впишите свой список |

Пока в полях стоит `REPLACE_...`, dry-run работает с предупреждением, а `--yes` откажется.

## 3. Токены

| Сайт | Переменная | Где взять |
|---|---|---|
| Modrinth | `MODRINTH_TOKEN` | modrinth.com → Settings → Personal access tokens (`mrp_...`). Права: *Create versions*, для обновления описания ещё *Write projects* |
| CurseForge | `CURSEFORGE_TOKEN` | authors.curseforge.com → Settings → API tokens |
| Hangar | `HANGAR_API_KEY` | hangar.papermc.io → меню профиля → API Keys; нужно право `create_version` |
| GitHub | `GITHUB_TOKEN` | github.com → Settings → Developer settings → fine-grained token: Contents: Read and write на репозиторий (классический: scope `repo`) |

PowerShell:

```powershell
$env:MODRINTH_TOKEN = "..."
$env:CURSEFORGE_TOKEN = "..."
$env:HANGAR_API_KEY = "..."
$env:GITHUB_TOKEN = "..."
```

bash:

```bash
export MODRINTH_TOKEN=...
export CURSEFORGE_TOKEN=...
export HANGAR_API_KEY=...
export GITHUB_TOKEN=...
```

Переменные живут только в текущем окне терминала. Не вписывайте токены в файлы.

## 4. GitHub

Проект пока не git-репозиторий, скрипт сам ничего не создаёт и при `--yes` остановится с ошибкой. Выполните:

```powershell
cd P:\FoliaRust\DiskUsagePlugin
git init -b main
# проверьте .gitignore: build/, данные тестовых серверов itest, токены не коммитить
git add .
git commit -m "DiskLens 2.0.0"
# создайте ПУСТОЙ репозиторий на github.com, затем:
git remote add origin https://github.com/ВЛАДЕЛЕЦ/ИМЯ.git
git push -u origin main
```

Релиз создаёт тег `v2.0.0` и прикладывает jar и `release/logo/disklens-icon-512.png`. Если установлен `gh`, используется он, иначе REST API.

## 5. Порядок запуска

```powershell
cd P:\FoliaRust\DiskUsagePlugin\release\publish

# 1) проверка (ничего не отправляет)
python publish.py all

# 2) реальная отправка по одной площадке
python publish.py github --yes
python publish.py modrinth --yes
python publish.py hangar --yes
python publish.py curseforge --yes
```

`python publish.py all --yes` отправит всё сразу, но по одной проще разбирать ошибки. Повторный запуск `--yes` создаст дубль версии или упадёт, так что не запускайте дважды.

Если задан `CURSEFORGE_TOKEN`, даже dry-run делает один безопасный GET `/api/game/versions` и проверяет, что все версии есть на CurseForge.

## 6. Что проверено и что нет

Эндпоинты и поля сверены с официальной документацией 2026-11-19 (см. комментарий в начале `publish.py`). Менее надёжные места:

* Hangar: передача API-ключа как `?apiKey=` в `/authenticate` и точная форма `versionUpload` взяты из пересказа OpenAPI-спецификации, не из полного чтения.
* Modrinth: префикс `/v2` взят из заметок документации.
* Скрипт ни разу не отправлял реальных запросов. Первый `--yes` на каждой площадке является первой реальной проверкой, смотрите вывод ошибок.
* Версии 26.x не включены (не тестировались).
* Текст changelog берётся из `release/hangar/changelog-2.0.0.md` (поле `changelog_file`). В нём сказано, что старый конфиг не переносится; проверьте это утверждение по коду перед публикацией.
