# Kế hoạch — Planner (Compose + Room)

Ứng dụng Android quản lý thời gian: công việc, lịch, ghi chú, thói quen và thống kê.
Viết bằng Kotlin, Jetpack Compose (Material 3), Room (SQLite), ViewModel + Flow.

## 5 màn hình

| Màn hình | Chức năng chính |
|---|---|
| **Hôm nay** | Lời chào, vòng tiến độ, gợi ý việc nên làm tiếp, timeline sáng/chiều/tối, việc quá hạn và sắp tới, ghi chú gắn với hôm nay, nút thêm nhanh |
| **Lịch** | Chế độ ngày / tuần / tháng, khối thời gian, kéo thả (nhấn giữ) để dời, phát hiện trùng giờ, lịch học/làm/hẹn lặp hằng tuần |
| **Công việc** | Deadline, thời lượng, ưu tiên, danh mục, việc con, lọc (hôm nay/sắp tới/quá hạn/xong), tìm kiếm, vuốt để hoàn thành hoặc xoá (có hoàn tác) |
| **Ghi chú** | Lưới/danh sách, màu, nhãn, danh mục, ghim, lưu trữ, checklist, liên kết với công việc hoặc ngày, tự lưu nháp, xuất/nhập JSON |
| **Thống kê** | Thói quen + chuỗi ngày (streak), hoàn thành theo tuần, thời gian theo danh mục, kế hoạch so với thực tế, heatmap hoạt động |

## Cấu trúc

- `data/` — entity, DAO, repository, `NotesDatabase` (Room, hiện là phiên bản 4, có migration 1→2→3→4)
  - `Scheduling.kt`: dựng khối thời gian, mở rộng lịch lặp, phát hiện trùng giờ, gợi ý ưu tiên
- `domain/` — mô hình, enum, `TimeUtils`, tính streak (hàm thuần, có unit test)
- `ui/` — theme, điều hướng (`nav/`), và từng màn hình (`today`, `calendar`, `tasks`, `notes`, `insights`)

## Chạy và kiểm thử

1. Mở bằng Android Studio, cài Android SDK, build và chạy trên máy ảo hoặc thiết bị.
2. Unit test (logic thuần): `./gradlew testDebugUnitTest`
3. Test trên thiết bị (DAO, repository, migration): `./gradlew connectedDebugAndroidTest`

Ngày được lưu dưới dạng epoch-day theo múi giờ của máy, thời điểm lưu dạng epoch millis.
