# Release signing

`stargate-release.jks` jest zaszyfrowanym, stałym kluczem podpisującym aplikację.

Nie zapisuj haseł w tym katalogu ani w historii Git. Są one przechowywane jako GitHub Actions Secrets. Utrata klucza lub haseł uniemożliwi podpisywanie aktualizacji zgodnych z wcześniej zainstalowaną aplikacją.
