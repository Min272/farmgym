# Nông Trại (MVP)

Game nông trại Android có 2 widget: **Đồng cỏ** và **Giếng**.

## Lấy APK qua GitHub
1. Tạo repo **Private**, đẩy toàn bộ nội dung thư mục này lên (phải có cả thư mục ẩn `.github`).
2. Vào tab **Actions** → **Build APK** (tự chạy khi push; hoặc bấm *Run workflow*).
3. Chạy xong, tải artifact `nongtrai-apk`, giải nén, cài `app-debug.apk` lên điện thoại (Android 8.0 trở lên).

Bằng máy tính (git):

    git init -b main
    git add .
    git commit -m "MVP"
    git remote add origin https://github.com/USER/REPO.git
    git push -u origin main

`debug.keystore` được để sẵn để bản mới cài đè được, không mất save. Key này chỉ dùng nội bộ.

## Thêm widget
Nhấn giữ màn hình chính → Widget → Nông Trại → kéo "Đồng cỏ" và "Giếng" ra.

## Cách chơi
Tưới cỏ → gà đói ăn cỏ → 2 giờ sau đẻ trứng → nhặt → cho xe đi bán (1 giờ).
Giếng cạn thì chạm widget giếng để bơm (30 phút).
Nút **Debug** trong app tua x1 / x60 / x600 để test.
Chỉnh thời gian, giá trong object `Rules` ở `app/src/main/java/com/tm/farm/Farm.kt`.

## Thay ảnh
Chép đè PNG **cùng tên** trong `app/src/main/res/drawable-nodpi/` (tên chỉ gồm a-z, 0-9, _).

| File | Nội dung | Cỡ (px) |
|---|---|---|
| chicken_eat0..7 | gà ăn | 128x128 |
| chicken_hungry0..7 | gà đói đi tại chỗ | 128x128 |
| chicken_idle | gà đứng | 128x128 |
| well_pump0..7 | giếng đang bơm | 192x192 |
| well_full, well_empty | giếng có / hết nước | 192x192 |
| egg | trứng và icon app | 128x128 |
| bg_pasture, grass | nền và cỏ (còn là ảnh tạm) | 400x260 |

Cắt lại từ sprite sheet: xem `tools/cut_sprites.py` (cần Pillow). Đổi kiểu giếng bằng `WELL_LEVEL`.

Giữ ảnh nhỏ vì widget bị giới hạn bộ nhớ ảnh.
