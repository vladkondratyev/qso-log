# Сторонние компоненты

| Компонент | Где используется | Лицензия |
|---|---|---|
| Atkinson Hyperlegible (Braille Institute of America) | шрифт интерфейса, `app/src/main/res/font/`, `desktop/src/main/resources/font/` | SIL Open Font License 1.1, [текст](licenses/OFL-AtkinsonHyperlegible.txt) |
| JetBrains Mono | шрифт позывных и чисел, там же | SIL Open Font License 1.1, [текст](licenses/OFL-JetBrainsMono.txt) |
| Russo One (Jovanny Lemonad) | логотип QSO-LOG, `russo_one.ttf` там же | SIL Open Font License 1.1, [текст](licenses/OFL-RussoOne.txt) |
| osmdroid | карты | Apache License 2.0 |
| AndroidX, Jetpack Compose, Material 3 | интерфейс | Apache License 2.0 |
| Kotlin, kotlinx.coroutines | язык и асинхронность | Apache License 2.0 |
| Compose Multiplatform (JetBrains) | интерфейс версии для компьютера | Apache License 2.0 |
| sqlite-jdbc (Xerial) | база данных версии для компьютера | Apache License 2.0 |
| java-keyring | хранение пароля в системной связке ключей | BSD 3-Clause |
| Eclipse Temurin (OpenJDK) | встроенная Java в сборках для macOS, Windows и Linux | GPLv2 with Classpath Exception |
| Launch4j | запускатель `QSO-LOG.exe` | BSD / MIT (заголовок exe) |

Картографические данные © участники OpenStreetMap, лицензия ODbL. Тайлы загружаются с серверов OpenStreetMap
по их [правилам использования](https://operations.osmfoundation.org/policies/tiles/).

Данные абонентов запрашиваются у [QRZ.ru XML API](https://www.qrz.ru/help/api/xml) по учётной записи пользователя.

## Справочные данные

- **cty.dat** (`shared/src/main/resources/cty.dat`) — файл стран DXCC, автор Jim Reisert AD1C, https://www.country-files.com.
  Включён в том виде, в каком его распространяет автор, как и в других программах-журналах (WSJT-X, Fldigi, N1MM);
  все права принадлежат автору.
- **Затухание коаксиальных кабелей** — сводная таблица Matthias DD1US (https://www.dd1us.de, апрель 2024)
  по паспортам производителей.
- **Субъекты РФ в любительских позывных** — список субъектов Российской Федерации и их идентификаторов,
  Союз радиолюбителей России (СРР), https://srr.ru.
- **Любительские диапазоны РФ** — решение ГКРЧ от 15 июля 2010 г. № 10-07-01.
