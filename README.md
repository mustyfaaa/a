# Jeoloji Radarı (Fabric 1.20.1)
E�itsel X-Ray: R tuşu ile aç/kapat, 16 blok çevredeki madenler duvar ardından renkli çerçeveyle görünür, HUD'da hangi Y seviyesinde oluştukları yazar.

## Derleme (bilgisayara hiçbir şey kurmadan)
1. Bu klasörü bir GitHub deposuna yükle.
2. Actions sekmesinde "build" çalışır; çıktı: artifact içindeki `.jar`.

## Derleme (yerelde)
JDK 17 + Gradle 8.5:  `gradle build`  → `build/libs/jeoloji-radari-1.0.0.jar`

## Kurulum
1. Fabric Loader 1.20.1 kur (fabricmc.net)
2. `.minecraft/mods` içine **Fabric API 0.92.2+1.20.1** ve `jeoloji-radari-1.0.0.jar` at.
3. Sadece tek oyunculu veya kendi sunucunda kullan; çoğu sunucuda hile sayılır.
