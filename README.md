# Kripto Vade Analiz

Tarayıcıda açılan, Binance USDⓈ-M Futures halka açık verisini kullanan teknik analiz panelidir.

## İçerik

- BTC, ETH, BNB, SOL, XRP, DOGE ve ADA için sembol seçimi
- 1 dakika, 5 dakika, 15 dakika, 1 saat, 4 saat ve 1 gün mumları
- Canlı Binance USDⓈ-M Futures WebSocket mum güncellemesi
- Binance ve MEXC perpetual sözleşme listelerini borsadan keşfetme
- Sembol/temel varlık araması ve borsa seçimi
- MEXC perpetual REST + WebSocket akışı
- Sol kenara kaydırınca 1.500 mumluk parçalarla 5 yıla kadar geriye dönük yükleme
- EMA 20/50/200, Bollinger Bands, VWAP, RSI, MACD ve ATR
- TradingView tarzı trend, ışın, yatay seviye, dikey çizgi, bölge ve Fibonacci çizimleri
- 1S / 4S / 1G üst zaman teyidi
- Kural tabanlı örnek geri test
- Giriş/stop/risk yüzdesi ile pozisyon boyutu hesaplayıcı
- Sinyal gerekçeleri ve oturum içi günlük

## Çalıştırma

`index.html` dosyasını internete bağlıyken açın. Bazı tarayıcılar `file://` üzerinden veri isteklerini kısıtlayabileceği için yerel bir HTTP sunucusu kullanmak daha sağlıklıdır:

```powershell
python -m http.server 8765 --directory .
```

Sonra `http://127.0.0.1:8765` adresine gidin.

## Önemli kapsam notu

Bu sürüm otomatik emir göndermez ve API anahtarı istemez. Sinyal skoru teknik gösterge uyumunu özetler; doğruluk veya kâr garantisi değildir. Geri test komisyon, spread, funding, kayma, likidite ve gerçek emir gerçekleşmesini tam modellemez. Gerçek para kullanmadan önce testnet/paper işlem, walk-forward test, komisyon/funding modellemesi ve out-of-sample doğrulama eklenmelidir.

TradingView Advanced Charts/Charting Library kendi piyasa verisini sağlamaz; veri beslemesinin uygulama sahibi veya yetkili üçüncü taraf tarafından sağlanması gerekir. Bu nedenle bu bağımsız sürüm TradingView tarzı arayüz/çizim yaklaşımını, Binance ve MEXC’nin halka açık perpetual REST + WebSocket verileriyle birleştirir. TradingView lisanslı veri beslemesi sağlanırsa ayrıca Datafeed API adaptörü eklenebilir. MEXC tarafında liste ve kline uçları bölgesel erişim, CORS veya borsa politika değişikliklerinden etkilenebilir.

Veri sağlayıcı ve çizim kütüphanesi değişirse bağlantı adresleri veya kütüphane sürümü güncellenmelidir.

