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
