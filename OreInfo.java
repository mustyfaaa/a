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
