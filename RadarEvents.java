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
