# Demento

Android-App für eine Einkaufsliste, die lokal auf dem Handy läuft. Artikel kommen per
Eintippen, Foto (Gemini, eigener API-Schlüssel) oder Barcode auf die Liste und werden
automatisch in Unterlisten nach Kategorie sortiert. Bei jeder Prüfung im Hintergrund (Abstand
einstellbar, Standard 15 Minuten) sucht die App jeden Artikel automatisch in allen Läden im
Umkreis (einstellbar, Standard 1 km) und meldet sich, wenn ein Laden ihn im Sortiment hat.
Niemand muss Artikel einem Laden zuordnen: Die App erkennt, was für ein Produkt ein Artikel ist
(eingebaute Wortliste, genauer mit Gemini), und kennt das übliche Sortiment der großen Ketten
(Lidl, Aldi, Rewe, Edeka, dm, Rossmann, OBI, MediaMarkt und viele mehr) und aller anderen
Ladenarten. Echte Lagerbestände veröffentlichen die Läden nicht.

Der Plan dazu steht in `/mnt/project-files/plan/shopping-list-app-plan.md`, technische
Details in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## APK herunterladen

Die neueste fertige APK gibt es hier:
<https://github.com/Multysquid/project-android1/releases/latest/download/demento.apk>

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

1. Die APK über den Link oben herunterladen oder eine selbst gebaute APK aufs Handy
   kopieren (z. B. per USB-Kabel in den Ordner „Download“) und in „Eigene Dateien“
   antippen. Wenn Android nachfragt: „Einstellungen“ → „Aus dieser Quelle zulassen“
   einschalten, zurückgehen und „Installieren“ tippen.
2. Blockiert Samsung die Installation, ist die „Automatische Sperre“ an (ab One UI 6). Dann
   unter *Einstellungen → Sicherheit und Datenschutz → Automatische Sperre* kurz ausschalten,
   die App installieren und die Sperre danach wieder einschalten.
3. In der App unter *Einstellungen* „Läden in der Nähe prüfen“ einschalten, beim Standort
   „Genau“ und danach „Immer zulassen“ wählen und *Samsung-Akku einrichten* durchgehen.
4. Optional, für Fotos und genaueres Einordnen: unter *Einstellungen → Gemini* einen eigenen
   Gemini-API-Schlüssel eintragen (erhältlich in Google AI Studio). Darunter lässt sich das
   Modell wählen; mit Schlüssel zeigt die Liste alle Modelle, die er nutzen darf. Standard ist
   Gemini 3.8 Flash. „Prüfen“ testet Schlüssel und Modell zusammen.

Die APK ist mit einem Debug-Schlüssel signiert, den jeder Build neu erzeugt. Lässt sich eine
neue Version deshalb nicht über die alte installieren, vorher in der App „Liste sichern“
nutzen, die alte App deinstallieren, die neue installieren und die Liste wieder importieren.

## Welche Daten das Handy verlassen

| Dienst | Wofür | Was gesendet wird |
|---|---|---|
| OpenStreetMap (Overpass-Server `overpass-api.de`, bei Störung ersatzweise `overpass.private.coffee`) | Läden in der Umgebung | Ein ungefährer Bereich: dein Standort auf etwa 100 m gerundet und der Umkreis |
| Open Food Facts und Schwesterdatenbanken (Open Products/Beauty/Pet Food Facts) | Barcode → Produktname | Nur der Barcode |
| Google Gemini (dein Schlüssel, nur bei „Foto“) | Foto → Name und Unterliste | Das Foto und die Namen deiner Unterlisten |
| Google Gemini (dein Schlüssel, nur mit „Artikel mit Gemini einordnen“: standardmäßig an, aber aus, wenn du schon vor dieser Einstellung einen Schlüssel eingetragen hattest) | Artikel → Produktart, damit die App weiß, in welchen Läden es ihn gibt | Nur der Name eines neuen Artikels, einmal pro Name |
| Google ML Kit (Barcode-Scanner) | Barcode lesen, auf dem Gerät | Keine Bilder; laut Google aber Nutzungs- und Leistungsdaten des Scanners (z. B. Gerätemodell, App-Version, Fehlercodes) |

Alles andere (Liste, Einkaufsverlauf, Einstellungen) bleibt auf dem Gerät. Auch die
Android-Sicherung in die Google-Cloud ist für die App abgeschaltet; für ein neues Handy gibt
es in den Einstellungen „Liste sichern“ (Exportieren und Importieren als Datei).

Kartendaten © OpenStreetMap-Mitwirkende (ODbL). Produktdaten: Open Food Facts und
Schwesterprojekte (ODbL).
