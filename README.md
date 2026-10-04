# Android Frameless Window

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="240" alt="Stargate WebView icon">
</p>

Natywna aplikacja Android wyświetlająca interfejs Stargate/FAN113 w pełnoekranowym, bezramkowym `WebView`. Projekt jest przeznaczony przede wszystkim dla tabletów pracujących w orientacji poziomej.

## Najważniejsze funkcje

- pełnoekranowy widok bez paska aplikacji;
- obsługa interfejsu DHD, dźwięku, gestów i wygaszacza;
- skalowanie pozostawione stronie FAN113 bez dodatkowego `scale-to-fit` po stronie Android WebView;
- usunięty systemowy niebieski efekt podświetlenia po dotknięciu elementów strony;
- możliwość uruchamiania jako zwykła aplikacja lub ekran główny tabletu;
- jeden APK przeznaczony dla tabletów różnych producentów.

## Wymagania

- Android 6.0 lub nowszy (`minSdk 23`);
- połączenie z siecią dla treści ładowanych przez WebView;
- orientacja pozioma.

## Budowanie lokalne

```powershell
.\gradlew.bat clean lintRelease assembleRelease
```

Wynik znajduje się w:

```text
app/build/outputs/apk/release/app-release.apk
```

Bez zmiennych podpisu lokalny build używa klucza debug. Oficjalne wydania z GitHuba są podpisywane stałym kluczem release przez GitHub Actions.

## Podpisywanie wydań

Zaszyfrowany plik klucza znajduje się w `signing/stargate-release.jks`. Hasła nie są przechowywane w kodzie ani historii Git — są zapisane jako GitHub Actions Secrets:

- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Workflow `.github/workflows/release.yml` buduje podpisany APK. Po wypchnięciu tagu w formacie `v*`, na przykład `v1.9-final`, tworzy również GitHub Release i dołącza APK.

## Instalacja

Pobierz APK z sekcji **Releases**, skopiuj go na tablet i zezwól Androidowi na instalowanie aplikacji z używanego menedżera plików lub przeglądarki. Do aktualizacji aplikacji należy zawsze używać APK podpisanego tym samym kluczem release.
