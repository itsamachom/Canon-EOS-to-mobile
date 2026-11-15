# Canon EOS to mobile — 600D Companion

แอป Android ภาษาไทยสำหรับ **Canon EOS 600D / Rebel T3i / Kiss X5** ผ่าน USB OTG
รุ่น **0.4.0-prototype** เน้นจอมอนิเตอร์และรับภาพ ล็อกแนวนอนทั้งแอป ไม่มีหน้าสั่งถ่ายหรือปรับค่าแสง

## ดาวน์โหลด

- **[APK 0.4.0](downloads/0.4.0/600D-Companion-0.4.0-prototype.apk?raw=true)** — เปิดแล้วกด Download raw file
- [คู่มือภาษาไทย](docs/INSTALL_TH.md) / [ซอร์ส ZIP](downloads/0.4.0/600D-Companion-0.4.0-source.zip?raw=true)
- [Checksum](downloads/0.4.0/SHA256SUMS) / [BUILD_INFO](downloads/0.4.0/BUILD_INFO.json)
- [สิ่งที่เปลี่ยน](docs/RELEASE_0.4.0.md) / [ผลทดสอบ](docs/TEST_RESULTS.md)

APK ใช้ติดตั้งบนมือถือ ไม่ใช่เฟิร์มแวร์กล้อง ไม่ต้องติดตั้ง ML เพิ่ม
เซ็นกุญแจ prototype เดียวกับ 0.1.1/0.2.0/0.3.0, versionCode 5 สำหรับติดตั้งทับ กุญแจส่วนตัวไม่รวมในซอร์ส
ผ่านการบิลด์และทดสอบจำลอง **ยังไม่ได้ลอง 0.4.0 บน Android/600D จริง** จึงไม่รับรองความเร็วหรือความแม่นยำทางแสง

## ใช้งาน

| หน้า | การทำงาน |
|---|---|
| เชื่อมต่อ | สิทธิ์ USB, รุ่นกล้อง/firmware, โหมดสาธิต, สถานะวินิจฉัย |
| ภาพในการ์ด | Thumbnail, เลือกหลายภาพ, JPEG เต็ม, ดาวน์โหลดครั้งละ 1–80 ภาพ |
| มอนิเตอร์ | เป้ารับคงที่ 12 fps, Peaking, Histogram, เส้นตาราง, ขยายจอ, เต็มจอ |

ปรับโหมด ค่าแสง โฟกัส กดถ่าย และเริ่ม/หยุดวิดีโอที่ตัวกล้อง ไม่มีคำสั่งเหล่านี้ใน UI/Android bridge
แอปยังเปิด/คืนค่า EVF เพื่อรับภาพ USB ตามเงื่อนไขเดิม แต่ไม่เขียนค่า ISO/รูรับแสง/ชัตเตอร์/EV

<img src="previews/04-monitor-demo.png" width="800" alt="หน้ามอนิเตอร์แนวนอนพร้อม Peaking และ Histogram ในโหมดสาธิต">

ภาพข้างบนเป็น screenshot จาก browser ที่ใช้ภาพจำลอง ไม่ใช่ผลทดสอบกล้องหรือ fps จริง

## Focus Peaking และ Histogram

เปิดทั้งสองอย่างให้ตั้งแต่เริ่ม แตะ **Peaking** / **Histogram** เพื่อซ่อนหรือแสดงได้
Peaking เน้นขอบภาพสีแดงด้วย Sobel บนภาพ JPEG ที่ย่อให้ด้านยาวไม่เกิน 320 px ไม่ใช่ autofocus หรือการยืนยันว่าตัวแบบเข้าโฟกัส
ขอบลายละเอียด/นอยส์อาจถูกเน้น ขอบคอนทราสต์ต่ำอาจไม่ขึ้น ควรตรวจด้วยตาหรือขยายภาพก่อนถ่ายจริง
Histogram เป็น luminance 256 ระดับ (รวมเป็น 128 แท่งบนจอ) จากเฟรม JPEG เต็มก่อนซูม ไม่ใช่ RAW หรือ RGB histogram
ความสว่างผ่านการประมวลผล/Picture Style ของกล้องแล้ว จึงไม่ใช้ยืนยัน RAW clipping
ทั้งสองคำนวณใหม่ในมือถือ ไม่คัดลอกโค้ดหรือดึง overlay ของ Magic Lantern และไม่เขียนลง JPEG ที่ดาวน์โหลด
ซ่อนผลวิเคราะห์เมื่อหยุด ภาพค้าง ออกจากหน้า หรือถอดสาย; หาก canvas อ่านภาพไม่ได้ จะแจ้งและยังแสดง Live View ต่อ

## Live View และข้อจำกัด

ตั้งเป้ารับคงที่ **12 fps** ไม่มีตัวเลือกเฟรมเรต ใช้เฟรมล่าสุด ไม่สะสมคิว ไม่ Base64/เขียนดิสก์ทุกเฟรม
ตัวเลขรับ/แสดงเป็นผลในแอป ไม่ใช่ fps เซ็นเซอร์หรือ MOV และไม่รับรองว่าจะได้ 12 จริงเมื่อ USB/กล้อง/มือถือช้า
เวลาหลังรับถึงแสดงไม่รวมเวลาภายในกล้องหรือ USB จึงไม่ใช่ motion-to-photon latency ทั้งระบบ
ภาพ USB PTP/EOS ไม่ใช่ HDMI/UVC และไม่ใช่สำเนาเมนู Canon/AF boxes/ML overlays ทั้งจอ
รับ JPEG segments 1/11 และ JPEG-in-9; segment 9 non-JPEG ยังไม่รองรับ
Movie/ขณะ REC ต้องลองกับกล้องจริง เริ่มมอนิเตอร์ก่อน REC แล้วกดที่กล้อง หากไม่มีเฟรมใหม่จะแจ้งภาพค้าง
ออกหน้ามอนิเตอร์/พักแอปจะหยุดรับและพยายามคืน EVF ที่แอปเปลี่ยน ไม่สั่งหยุด REC

## ภาพในการ์ด

Thumbnail ใช้ในตาราง ดูชัดอ่าน JPEG ต้นฉบับผ่าน GetObject ไม่มีการบีบอัดใหม่ รองรับ JPEG ≤64 MiB
เลือกได้ไม่เกิน 80 ภาพ เลือกโฟลเดอร์ Android ครั้งเดียวต่อชุด รับทีละไฟล์ และหยุดหลังไฟล์ปัจจุบันได้
ไม่ลบต้นฉบับ Cache 128 MiB ไม่ใช่ที่เก็บถาวร ยังไม่ดาวน์โหลด RAW/MLV

## ติดตั้งและพัฒนา

Android 8+ รองรับ USB Host/OTG, WebView ที่อัปเดต และสาย USB Mini-B ที่ส่งข้อมูลได้
แอปขอแนวนอนผ่าน manifest; ต้องลองการหมุน/ระบบหลายหน้าต่างกับมือถือจริง Android บางอุปกรณ์อาจ override orientation
Linux x86_64, Java 21+, Python 3.9+, Node.js; Android API35/build-tools35.0.0/ECJ3.39.0 ไม่มี Gradle/AndroidX

```sh
python3 tools/setup-tools.py build/toolchain
bash test.sh
bash build-android.sh
```

APK อยู่ `build/600D-Companion-0.4.0-prototype.apk` กำหนด TOOLCHAIN เพื่อใช้เครื่องมือที่ติดตั้งแล้ว
อย่าลบ build/prototype.keystore ถ้าต้องการเซ็นอัปเดตด้วยกุญแจของคุณต่อไป การบิลด์เองไม่มีกุญแจเดิมจะได้ลายเซ็นใหม่
SDK/ECJ มีใบอนุญาตแยกจากซอร์สแอป

ทดสอบ browser ด้วย Node/Playwright/Chromium:

```sh
python3 -m http.server 8765 --directory app/src/main/assets
# อีก terminal:
NODE_PATH=/path/to/node_modules node tests/ui.cjs
```

ปรับ PREVIEW_URL / CHROMIUM_PATH ให้ตรงเครื่องได้ ผลทดสอบและจำนวนอยู่ BUILD_INFO
Android installer, orientation, folder picker, lifecycle, USB, optical focus และ fps จริงยังต้องทดสอบบนอุปกรณ์

## ที่มาและใบอนุญาต

โครงการ Android แยกจาก [EOS_ROM_MLV_Modify](https://github.com/itsamachom/EOS_ROM_MLV_Modify)
ไม่มี ML/Canon firmware, ROM, cloud credential หรือ private signing key รวมใน repository
PTP/EOS implementation เขียนใหม่เพื่อ interoperability ไม่รวม libgphoto2; ตรวจ operation/property และลำดับจาก:

- [library.c](https://github.com/gphoto/libgphoto2/blob/master/camlibs/ptp2/library.c): 600D USB ID, shutter event handling, EVF setup และ frame segments
- [ptp.h](https://github.com/gphoto/libgphoto2/blob/master/camlibs/ptp2/ptp.h): PTP/EOS identifiers
- [ptp.c](https://github.com/gphoto/libgphoto2/blob/master/camlibs/ptp2/ptp.c): EOS property data-out layout และ GetViewFinderData arguments
- [ptp-pack.c](https://github.com/gphoto/libgphoto2/blob/master/camlibs/ptp2/ptp-pack.c): event layout / CameraStatusChanged
- [config.c](https://github.com/gphoto/libgphoto2/blob/master/camlibs/ptp2/config.c): property types และ recording status

ซอร์สแอปใช้ [MIT](LICENSE) มี Codex/LLM ช่วยพัฒนา/ตรวจ ไม่ใช่ Canon Camera Connect และไม่ได้รับการรับรองจาก Canon/Magic Lantern/libgphoto2
[0.3.0 เดิม](downloads/0.3.0/), [0.2.0](downloads/0.2.0/) และ [0.1.1](downloads/0.1.1/) ยังคงอยู่เพื่ออ้างอิงและย้อนกลับ
