# Nông Trại (MVP)

Game nông trại Android với **1 widget lớn** gồm bãi đất, giếng và xe tải.

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
Nhấn giữ màn hình chính → Widget → Nông Trại → kéo **Nông trại** ra (mặc định 4x4, kéo to nhỏ được).

## Chạm trên widget (lưới ẩn 4 cột x 3 hàng)
| Chạm | Kết quả |
|---|---|
| Giếng | bơm đầy (30 phút, 5 xu) |
| Xe tải | chở trứng trong kho đi bán (1 giờ) |
| Ô trên bãi | nhặt trứng trong ô đó (và trứng nằm sát mép ô) |
| ⚙ góc trên phải | mở app |

## Cách chơi
- Giếng có nước thì **tự tưới** 10 phút một lần, mỗi lần 5-7 đám cỏ. Tối đa 200 đám.
- Gà đói chạy tới đám gần nhất ăn 5 phút, cỏ thưa dần. No 60 phút rồi đói.
- Đủ 120 phút không đói thì đẻ 1 trứng ngay dưới chân.
- Gà đói liên tục **12 giờ** thì chết. Trứng để quá **10 giờ** thì biến mất.
- Chết hết gà vẫn mua lại được dù thiếu tiền.
Widget chỉ mô phỏng: gà đi qua lại quanh vị trí tính ở lần cập nhật gần nhất.

Nút **Debug** trong app tua x1 / x60 / x600 để test.
Chỉnh thời gian, giá trong object `Rules` ở `app/src/main/java/com/tm/farm/Farm.kt`.

## Thay ảnh
PNG trong `app/src/main/res/drawable-nodpi/`, chép đè đúng tên:
`chicken_eat0..7`, `chicken_hungry0..7`, `chicken_idle`, `walk0_0..walk4_7`, `walk_right0..7`,
`well_full`, `well_empty`, `well_pump0..7`, `egg`, `truck`, `grass_0..15` (0 thưa nhất, 15 rậm nhất),
`map_wide`, `map_square`, `map_tall`.

Script tạo lại ảnh (cần Pillow): `tools/cut_sprites.py` (gà, giếng, trứng), `tools/make_map.py` (map, cỏ, xe).
Đổi hình map thì chỉnh vùng đất `Field` (Farm.kt) và vị trí giếng, xe (Scene.kt).
