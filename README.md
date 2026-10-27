# Einkaufsliste

Android-App für eine Einkaufsliste, die lokal auf dem Handy läuft. Artikel kommen per
Eintippen, Foto (Gemini, eigener API-Schlüssel) oder Barcode auf die Liste und werden
automatisch in Unterlisten nach Kategorie sortiert. Im Hintergrund prüft die App, ob ein
Laden im eingestellten Umkreis (Standard 500 m) wahrscheinlich etwas von der Liste führt,
und meldet sich dann.

Der Plan dazu steht in `/mnt/project-files/plan/shopping-list-app-plan.md`, technische
Details in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## APK herunterladen

Die neueste fertige APK gibt es hier:
<https://github.com/Multysquid/project-android1/releases/latest/download/einkaufsliste.apk>

GitHub baut sie bei jeder Änderung neu (`.github/workflows/apk.yml`). Den Link einfach auf
dem Handy öffnen und weiter bei [Auf das Handy bringen](#auf-das-handy-bringen).

## Bauen

Voraussetzungen: JDK 17 oder 21 (der Gradle-Wrapper 8.14 läuft nicht mit JDK 25) und das
Android SDK (Plattform 35). Am einfachsten öffnest du den Ordner in Android Studio, das
beides mitbringt, und startest die App auf dem Handy.

Auf der Kommandozeile muss Gradle das SDK finden: entweder `ANDROID_HOME` setzen oder im
Projektordner eine Datei `local.properties` mit `sdk.dir=/pfad/zum/Android/Sdk` anlegen
(Android Studio legt sie selbst an). Dann:

```sh
./gradlew :app:assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

Die Kernlogik (Ladenabgleich, Öffnungszeiten, Barcode- und Gemini-Auswertung) lässt sich
ohne Android SDK testen:

```sh
./gradlew -p core test
```

## Auf das Handy bringen

1. Die APK auf dem Handy herunterladen (Link oben) oder eine selbst gebaute APK aufs Handy
   kopieren (z. B. per USB-Kabel in den Ordner „Download“) und in „Eigene Dateien“
   antippen. Wenn Android nachfragt: „Einstellungen“ → „Aus dieser Quelle zulassen“
   einschalten, zurückgehen und „Installieren“ tippen.
2. Blockiert Samsung die Installation, ist die „Automatische Sperre“ an (ab One UI 6). Dann
   unter *Einstellungen → Sicherheit und Datenschutz → Automatische Sperre* kurz ausschalten,
   die App installieren und die Sperre danach wieder einschalten.
3. In der App unter *Einstellungen* „Läden in der Nähe prüfen“ einschalten, beim Standort
   „Genau“ und danach „Immer zulassen“ wählen und *Samsung-Akku einrichten* durchgehen.
4. Optional, für Fotos: unter *Einstellungen → Foto-Erkennung (Gemini)* einen eigenen
   Gemini-API-Schlüssel eintragen (erhältlich in Google AI Studio).

Die APK ist mit einem Debug-Schlüssel signiert, den jeder Build neu erzeugt. Lässt sich eine
neue Version deshalb nicht über die alte installieren, vorher in der App „Liste sichern“
nutzen, die alte App deinstallieren, die neue installieren und die Liste wieder importieren.

## Welche Daten das Handy verlassen

| Dienst | Wofür | Was gesendet wird |
|---|---|---|
| OpenStreetMap (Overpass-Server `overpass-api.de`, bei Störung ersatzweise `overpass.private.coffee`) | Läden in der Umgebung | Ein ungefährer Bereich: dein Standort auf etwa 100 m gerundet und der Umkreis |
| Open Food Facts und Schwesterdatenbanken (Open Products/Beauty/Pet Food Facts) | Barcode → Produktname | Nur der Barcode |
| Google Gemini (dein Schlüssel, nur bei „Foto“) | Foto → Name und Unterliste | Das Foto und die Namen deiner Unterlisten |
| Google ML Kit (Barcode-Scanner) | Barcode lesen, auf dem Gerät | Keine Bilder; laut Google aber Nutzungs- und Leistungsdaten des Scanners (z. B. Gerätemodell, App-Version, Fehlercodes) |

Alles andere (Liste, Einkaufsverlauf, Einstellungen) bleibt auf dem Gerät. Auch die
Android-Sicherung in die Google-Cloud ist für die App abgeschaltet; für ein neues Handy gibt
es in den Einstellungen „Liste sichern“ (Exportieren und Importieren als Datei).

Kartendaten © OpenStreetMap-Mitwirkende (ODbL). Produktdaten: Open Food Facts und
Schwesterprojekte (ODbL).
