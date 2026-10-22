
plugins {
    id 'fabric-loom' version '1.5-SNAPSHOT'
}

version = project.mod_version
group = project.maven_group
base { archivesName = project.archives_base_name }

repositories { }

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}

processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") { expand "version": project.version }
}

tasks.withType(JavaCompile).configureEach { it.options.release = 17 }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

// ============================================================
//  DOSYA 1/3: ANA SINIF
// ============================================================
//  Bir Minecraft modunun "giriş kapısı" budur.
//  Oyun açılırken Fabric bu dosyayı bulur ve onInitializeClient()
//  fonksiyonunu BİR KEZ çalıştırır.
//
//  Bu dosyanın tek görevi: R tuşunu tanıtmak ve diğer parçaları başlatmak.
//
//  [VİDEODA ANLATILIR] "Her mod bir giriş noktasına sahiptir.
//  Bunu fabric.mod.json dosyasında 'entrypoints' kısmında belirttik."
// ============================================================

// "package" = bu dosyanın hangi klasörde durduğunu söyler.
package com.jeolojiradari;

// "import" = başka yerlerde yazılmış hazır kodları kullanmak için çağırırız.
import net.fabricmc.api.ClientModInitializer;                       // Sadece istemcide (oyuncu tarafında) çalışan mod girişi
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper; // Tuş kaydetmeyi kolaylaştıran yardımcı
import net.minecraft.client.option.KeyBinding;                      // Minecraft'taki "tuş" nesnesi
import net.minecraft.client.util.InputUtil;                         // Klavye/fare türleri
import org.lwjgl.glfw.GLFW;                                         // Tuş kodlarını (R, F, G...) barındırır

// "implements ClientModInitializer" = "Ben bir mod giriş sınıfıyım" demek.
public class JeolojiRadariClient implements ClientModInitializer {

    // Modun kısa adı. Dosya adlarında ve kayıtlarda kullanılır.
    public static final String MOD_ID = "jeolojiradari";

    // Radarın tarama yarıçapı (blok cinsinden).
    // 16 yazarsak oyuncunun her yönde 16 blok ilerisine bakılır.
    // [VİDEODA ANLATILIR] "Bu sayıyı büyütürsek bilgisayar yorulur,
    //  çünkü taranacak blok sayısı çok hızlı artar. Neden? Birazdan hesaplayacağız."
    public static final int RADIUS = 16;

    // Radarı açıp kapatan tuşumuz. "static" = tüm dosyalardan erişilebilir.
    // Başta boş (null), onInitializeClient içinde dolduruyoruz.
    public static KeyBinding toggleKey;

    // Oyun açılırken Fabric bu fonksiyonu çağırır.
    @Override
    public void onInitializeClient() {

        // Yeni bir tuş oluşturup Minecraft'a kaydediyoruz.
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.jeolojiradari.toggle",   // 1) Tuşun kimliği. Adı lang/ klasöründeki dosyadan gelir.
                InputUtil.Type.KEYSYM,        // 2) Klavye tuşu (fare olsaydı MOUSE olurdu)
                GLFW.GLFW_KEY_R,              // 3) Varsayılan tuş: R
                "category.jeolojiradari"));   // 4) Kontroller menüsünde hangi başlık altında görüneceği

        // Tuş hazır. Şimdi "olay dinleyicilerini" başlatıyoruz.
        // [VİDEODA ANLATILIR] "Event = olay. 'Oyun bir tick geçti', 'ekran çizildi' gibi
        //  anlarda bizim kodumuzun çağrılmasını sağlarız."
        RadarEvents.register();
    }
}

// ============================================================
//  DOSYA 2/3: MADEN BİLGİ TABLOSU
// ============================================================
//  Radarın "ansiklopedisi". Her maden için şunları tutar:
//    - Türkçe adı
//    - Çerçeve rengi
//    - En alt ve en üst oluşum seviyesi (Y)
//    - En bol bulunduğu seviye (peakY)
//
//  Değerler Minecraft 1.20.1'in dünya üretimine göre yaklaşık değerlerdir.
//  [VİDEODA ANLATILIR] "Y koordinatı yüksekliktir. F3 tuşuna basınca
//   ekranda görünen Y değeri bu. -64 en dip (bedrock), 320 gökyüzü sınırı."
// ============================================================

package com.jeolojiradari;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.HashMap;
import java.util.Map;

// "record" = sadece veri tutan basit bir kutu. Java otomatik olarak
// name(), color(), minY() gibi okuma fonksiyonlarını bizim için üretir.
public record OreInfo(String name, int color, int minY, int maxY, int peakY) {

    // Sözlük gibi çalışır: "Hangi blok -> hangi maden bilgisi?"
    // Örnek: ORES.get(Blocks.DIAMOND_ORE) -> Elmas bilgisi
    // [VİDEODA ANLATILIR] "Map yapısı bir sözlüktür. Kelime = blok, anlam = maden bilgisi."
    public static final Map<Block, OreInfo> ORES = new HashMap<>();

    // Yardımcı fonksiyon: Aynı bilgiyi birden fazla bloğa bağlar.
    // Neden birden fazla? Her madenin iki hali var: normal taş içinde
    // ve "deepslate" (derin kayrak) içinde. İkisi de aynı madendir.
    private static void put(OreInfo info, Block... blocks) {
        for (Block b : blocks) {
            ORES.put(b, info);
        }
    }

    // "static { }" = sınıf yüklenirken bir kez çalışan kısım.
    // Tabloyu burada dolduruyoruz.
    // Renkler 0xRRGGBB biçiminde (kırmızı-yeşil-mavi, 16'lık sayı sistemi).
    static {
        //                 ad          renk      minY  maxY  peakY
        put(new OreInfo("Kömür",    0x303030,     0,  320,   96), Blocks.COAL_ORE,     Blocks.DEEPSLATE_COAL_ORE);
        put(new OreInfo("Demir",    0xFF8C00,   -64,   72,   16), Blocks.IRON_ORE,     Blocks.DEEPSLATE_IRON_ORE);
        put(new OreInfo("Bakır",    0xCD7F32,   -16,  112,   48), Blocks.COPPER_ORE,   Blocks.DEEPSLATE_COPPER_ORE);
        put(new OreInfo("Altın",    0xFFD700,   -64,   32,  -16), Blocks.GOLD_ORE,     Blocks.DEEPSLATE_GOLD_ORE);
        put(new OreInfo("Lapis",    0x2255FF,   -64,   64,    0), Blocks.LAPIS_ORE,    Blocks.DEEPSLATE_LAPIS_ORE);
        put(new OreInfo("Kızıltaş", 0xFF2020,   -64,   15,  -59), Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
        put(new OreInfo("Elmas",    0x00E5FF,   -64,   16,  -59), Blocks.DIAMOND_ORE,  Blocks.DEEPSLATE_DIAMOND_ORE);
        put(new OreInfo("Zümrüt",   0x00C853,   -16,  320,  236), Blocks.EMERALD_ORE,  Blocks.DEEPSLATE_EMERALD_ORE);
        // [VİDEODA ANLATILIR] "Elmasın neden hep derinde çıktığını bu tablodan görüyoruz:
        //  en bol Y -59'da. Zümrüt ise sadece dağ biyomlarında ve çok yüksekte."
    }

    // --- Renk yardımcıları ---
    // Ekrana çizerken renk 0.0 ile 1.0 arasında istenir.
    // Elimizdeki 0-255 değerini 255'e bölerek çeviriyoruz.
    public float r() { return ((color >> 16) & 0xFF) / 255f; } // kırmızı kısmı
    public float g() { return ((color >> 8)  & 0xFF) / 255f; } // yeşil kısmı
    public float b() { return ( color        & 0xFF) / 255f; } // mavi kısmı

    // Ekrana yazılacak eğitici cümle.
    public String lesson() {
        return minY + " ile " + maxY + " arası oluşur, en bol Y " + peakY;
    }
}

// ============================================================
//  DOSYA 3/3: OLAY DİNLEYİCİLERİ (modun asıl işi burada)
// ============================================================
//  Üç şey yapar:
//    1) TICK   : Tuşa basıldı mı? Basıldıysa aç/kapat. Açıksa çevreyi tara.
//    2) RENDER : Bulunan madenlerin etrafına duvar ardından çerçeve çiz.
//    3) HUD    : Ekranın köşesine bilgi yaz.
//
//  [VİDEODA ANLATILIR] "Minecraft saniyede 20 kez 'tick' atar.
//   Oyunun tüm mantığı bu tick'lerle ilerler. Biz de bu ritme katılıyoruz."
// ============================================================

package com.jeolojiradari;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// "final" = bu sınıftan başka sınıf türetilemez. Zaten sadece fonksiyon barındırıyor.
public final class RadarEvents {

    // Bulduğumuz her madeni "nerede + ne" olarak saklayan küçük kutu.
    private record Found(BlockPos pos, OreInfo info) {}

    // ----- RADARIN HAFIZASI (değişkenler) -----

    // Radar şu an açık mı? false = kapalı, true = açık.
    private static boolean enabled = false;

    // Tick sayacı. 20 olunca (= 1 saniye) bir tarama yaparız.
    // [VİDEODA ANLATILIR] "Neden her tick taramıyoruz? Çünkü 36 bin bloğu saniyede
    //  20 kez kontrol etmek gereksiz yük olur. Saniyede 1 yeter."
    private static int tickCounter = 0;

    // Son taramada bulunan madenler. Çizim ve HUD bu listeyi okur.
    private static List<Found> found = new ArrayList<>();

    // Oyuncunun yüksekliği (HUD'da göstermek için).
    private static int playerY = 0;

    // Dışarıdan nesne oluşturulmasın diye boş ve gizli.
    private RadarEvents() {}

    // =========================================================
    //  KAYIT: Event'leri Minecraft'a tanıtıyoruz
    // =========================================================
    public static void register() {
        // "Her tick bittiğinde onTick fonksiyonumu çağır."
        ClientTickEvents.END_CLIENT_TICK.register(RadarEvents::onTick);

        // "Dünya çizildikten sonra render fonksiyonumu çağır."
        // LAST = en son çizilen şey bizimki olsun, her şeyin üstüne gelsin.
        WorldRenderEvents.LAST.register(ctx -> render(ctx.matrixStack(), ctx.camera()));

        // "Ekran arayüzü (HUD) çizilirken renderHud fonksiyonumu çağır."
        HudRenderCallback.EVENT.register(RadarEvents::renderHud);
    }

    // =========================================================
    //  1) TICK: tuş kontrolü + tarama
    // =========================================================
    private static void onTick(MinecraftClient client) {

        // --- Tuş kontrolü ---
        // wasPressed() = "son tick'ten beri tuşa basıldı mı?" Basıldıysa true döner.
        // "while" kullandık çünkü bir tick içinde birden fazla basış olabilir.
        while (JeolojiRadariClient.toggleKey.wasPressed()) {
            enabled = !enabled;              // true ise false, false ise true yap
            if (!enabled) {
                found = new ArrayList<>();   // Kapatınca eski sonuçları temizle
            }
            tickCounter = 20;                // Açılınca 1 saniye beklemeden hemen tara
        }

        // Radar kapalıysa burada çık, hiçbir iş yapma. (Oyun performansı için önemli)
        if (!enabled) return;

        // --- OYUNCU VERİLERİNE ERİŞİM ---
        // client.player = bizim karakterimiz, client.world = içinde bulunduğumuz dünya.
        ClientPlayerEntity player = client.player;
        World world = client.world;

        // Ana menüdeyken veya dünya yüklenirken bunlar boş (null) olur.
        // Boşken kullanmaya çalışırsak oyun çöker, o yüzden kontrol ediyoruz.
        // [VİDEODA ANLATILIR] "Buna 'null kontrolü' denir. Programcıların en sık yaptığı hata kaynağı!"
        if (player == null || world == null) return;

        // Oyuncunun bulunduğu blok koordinatı. Y = yükseklik.
        BlockPos origin = player.getBlockPos();
        playerY = origin.getY();

        // --- Saniyede bir tarama ---
        tickCounter++;
        if (tickCounter < 20) return;   // Henüz 1 saniye dolmadı, çık.
        tickCounter = 0;                // Sayacı sıfırla

        scan(world, origin);
    }

    // Oyuncunun etrafındaki küpü tarar ve madenleri listeye ekler.
    private static void scan(World world, BlockPos origin) {
        int r = JeolojiRadariClient.RADIUS;
        List<Found> result = new ArrayList<>();

        // Tekrar tekrar yeni nesne üretmemek için tek bir "değiştirilebilir konum" kullanıyoruz.
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        // Üç iç içe döngü = bir küpün her noktasını gez.
        // x: -16'dan +16'ya, y: -16'dan +16'ya, z: -16'dan +16'ya
        // Toplam: 33 x 33 x 33 = 35.937 blok. Saniyede bir kez. Bilgisayar için kolay iş.
        // [VİDEODA ANLATILIR] "Yarıçapı 64 yapsak 129³ = 2 milyon blok olur. Neden donacağını şimdi anladık."
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {

                    // Bakılacak konumu ayarla: oyuncunun konumu + kayma miktarı
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);

                    // Dünyanın dışına (çok yukarı/aşağı) bakmayalım.
                    if (world.isOutOfHeightLimit(cursor)) continue;

                    // Bu konumdaki blok ne? Sözlükte (ORES) var mı?
                    // Varsa maden, yoksa null döner.
                    OreInfo info = OreInfo.ORES.get(world.getBlockState(cursor).getBlock());

                    if (info != null) {
                        // toImmutable() = konumun kalıcı bir kopyasını al (cursor sürekli değişiyor).
                        result.add(new Found(cursor.toImmutable(), info));
                    }
                }
            }
        }
        found = result; // Eski listeyi yenisiyle değiştir.
    }

    // =========================================================
    //  2) RENDER: madenlerin etrafına çerçeve çiz
    // =========================================================
    //  [VİDEODA ANLATILIR] "Gerçek X-Ray hilelerinin yaptığı şey de budur:
    //   'Derinlik testi'ni kapatıp nesneleri duvarın önünde çizmek."
    private static void render(MatrixStack matrices, Camera camera) {
        if (!enabled || found.isEmpty()) return; // Kapalıysa veya madenyoksa çizme.

        // Kameranın dünyadaki konumu. Çizimleri kameraya göre kaydırmamız lazım,
        // çünkü ekran her zaman "bizim gözümüzü" merkez alır.
        Vec3d cam = camera.getPos();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        // --- Çizim ayarları ---
        RenderSystem.disableDepthTest();   // ★ Asıl sihir: duvarların ardını da göster
        RenderSystem.enableBlend();        // Saydamlığa izin ver
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram); // "Konum + renk" boyayıcısı
        RenderSystem.lineWidth(2.5f);      // Çizgi kalınlığı

        // BufferBuilder = çizeceğimiz çizgileri biriktirdiğimiz defter.
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // Bulunan her maden için bir küp çerçevesi çiz.
        for (Found f : found) {
            // Küpün köşe koordinatları (kameraya göre kaydırılmış).
            float x1 = (float) (f.pos().getX() - cam.x);
            float y1 = (float) (f.pos().getY() - cam.y);
            float z1 = (float) (f.pos().getZ() - cam.z);
            float x2 = x1 + 1, y2 = y1 + 1, z2 = z1 + 1; // 1 blokluk küp

            // Madenin rengi
            float r = f.info().r(), g = f.info().g(), b = f.info().b();

            // Bir küpün 12 kenarı var: 4 alt, 4 üst, 4 dikey.
            // Alt yüz
            line(buf, mat, x1,y1,z1, x2,y1,z1, r,g,b);
            line(buf, mat, x2,y1,z1, x2,y1,z2, r,g,b);
            line(buf, mat, x2,y1,z2, x1,y1,z2, r,g,b);
            line(buf, mat, x1,y1,z2, x1,y1,z1, r,g,b);
            // Üst yüz
            line(buf, mat, x1,y2,z1, x2,y2,z1, r,g,b);
            line(buf, mat, x2,y2,z1, x2,y2,z2, r,g,b);
            line(buf, mat, x2,y2,z2, x1,y2,z2, r,g,b);
            line(buf, mat, x1,y2,z2, x1,y2,z1, r,g,b);
            // Dikey kenarlar
            line(buf, mat, x1,y1,z1, x1,y2,z1, r,g,b);
            line(buf, mat, x2,y1,z1, x2,y2,z1, r,g,b);
            line(buf, mat, x2,y1,z2, x2,y2,z2, r,g,b);
            line(buf, mat, x1,y1,z2, x1,y2,z2, r,g,b);
        }

        // Defterdeki tüm çizgileri tek seferde ekrana bas.
        Tessellator.getInstance().draw();

        // --- Ayarları eski haline getir ---
        // Yoksa oyunun geri kalanı bozuk çizilir!
        // [VİDEODA ANLATILIR] "Çizim ayarlarını değiştirdiysek mutlaka geri alırız. Ortalığı dağıtıp gitmeyiz."
        RenderSystem.lineWidth(1f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // Tek bir çizgi çizer: (x1,y1,z1) noktasından (x2,y2,z2) noktasına, verilen renkte.
    // Her çizgi 2 uç noktadan oluşur. 1f = tamamen opak (saydam değil).
    private static void line(BufferBuilder buf, Matrix4f m,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, 1f).next();
        buf.vertex(m, x2, y2, z2).color(r, g, b, 1f).next();
    }

    // =========================================================
    //  3) HUD: ekrandaki yazılar
    // =========================================================
    private static void renderHud(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;     // Yazı çizici

        // F1 ile arayüz gizliyse (video çekerken sık yapılır) yazı çizme.
        if (client.options.hudHidden) return;

        int x = 6, y = 6; // Sol üst köşeden 6 piksel içeride başla.

        // Radar kapalıyken sadece küçük bir hatırlatma göster.
        if (!enabled) {
            ctx.drawTextWithShadow(tr, Text.literal("Jeoloji Radarı: KAPALI (R)"), x, y, 0xAAAAAA);
            return;
        }

        // Radar açık: başlık satırı + oyuncunun Y seviyesi
        ctx.drawTextWithShadow(tr, Text.literal("Jeoloji Radarı: AÇIK  |  Y = " + playerY), x, y, 0x55FF55);
        y += 12; // Bir alt satıra in.

        // Maden türlerine göre say: "Elmas -> 3 tane, Demir -> 12 tane" gibi.
        // LinkedHashMap = eklenme sırasını korur.
        Map<OreInfo, Integer> counts = new LinkedHashMap<>();
        for (Found f : found) {
            counts.merge(f.info(), 1, Integer::sum); // Varsa +1, yoksa 1 yaz
        }

        // Hiç maden yoksa bilgi ver ve çık.
        if (counts.isEmpty()) {
            ctx.drawTextWithShadow(tr, Text.literal("Yakında maden yok (" + JeolojiRadariClient.RADIUS + " blok)."), x, y, 0xFFFFFF);
            return;
        }

        // Her maden türü için iki satır yaz: ad + adet, altında eğitici bilgi.
        for (Map.Entry<OreInfo, Integer> e : counts.entrySet()) {
            OreInfo info = e.getKey();

            // Madenin kendi renginde ad. "| 0xFF000000" = tamamen opak yap.
            ctx.drawTextWithShadow(tr, Text.literal(info.name() + " x" + e.getValue()),
                    x, y, info.color() | 0xFF000000);
            y += 10;

            // ★ EĞİTSEL KISIM: Oyuncu şu an bu madenin normal oluşum aralığında mı?
            // [VİDEODA ANLATILIR] "Radar sadece göstermiyor, 'neden burada?' sorusunu da yanıtlıyor."
            boolean typical = playerY >= info.minY() && playerY <= info.maxY();
            String tip = "  " + info.lesson() + (typical ? "  (şu an uygun derinlik)" : "");
            ctx.drawTextWithShadow(tr, Text.literal(tip), x, y, 0xCCCCCC);
            y += 10;
        }
    }
}

{
  "schemaVersion": 1,
  "id": "jeolojiradari",
  "version": "${version}",
  "name": "Jeoloji Radarı",
  "description": "Eğitsel X-Ray: R tuşuyla maden taraması ve Y seviyesi bilgisi.",
  "authors": ["Sen"],
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "client": ["com.jeolojiradari.JeolojiRadariClient"]
  },
  "depends": {
    "fabricloader": ">=0.15.0",
    "minecraft": "1.20.1",
    "java": ">=17",
    "fabric-api": "*"
  }
}

{
  "key.jeolojiradari.toggle": "Jeoloji Radarını Aç/Kapat",
  "category.jeolojiradari": "Jeoloji Radarı"
}

{
  "key.jeolojiradari.toggle": "Toggle Geology Radar",
  "category.jeolojiradari": "Geology Radar"
}

name: build
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: 8.5
      - run: gradle build
      - uses: actions/upload-artifact@v4
        with:
          name: jeoloji-radari
          path: build/libs/*.jar

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

org.gradle.jvmargs=-Xmx1G
minecraft_version=1.20.1
yarn_mappings=1.20.1+build.10
loader_version=0.15.11
fabric_version=0.92.2+1.20.1
mod_version=1.0.0
maven_group=com.jeolojiradari
archives_base_name=jeoloji-radari

pluginManagement {
    repositories {
        maven { url = 'https://maven.fabricmc.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}
