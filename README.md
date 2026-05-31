# 轮班日历 Android App

这是一个原生 Android 示例项目，包含：

- 2026 年中国大陆节假日与调休日历。
- 2027 年预置节假日、周末日历；调休安排待官方发布后更新。
- 用户可设置轮班周期，例如 4 天一周期，并逐日输入白班、夜班、休息等工作类型。
- 月历中直接显示节假日、调休上班日和每天对应的轮班类型。
- 可按不同班次保存多个闹钟时间，并通过系统 `AlarmClock` SDK 创建系统闹钟。

## 打开方式

1. 用 Android Studio 打开当前目录。
2. 等待 Gradle 同步。
3. 运行 `app` 到安卓设备或模拟器。

项目使用 Java + 原生 View，不依赖第三方 UI 框架。

## 在线编译 APK

项目已添加 GitHub Actions 工作流：

```text
.github/workflows/build-apk.yml
```

同步到 GitHub 后，可以这样生成 APK：

1. 打开 GitHub 仓库页面。
2. 进入 `Actions`。
3. 选择 `Build Android APK`。
4. 点击 `Run workflow`。
5. 等待构建完成后，在构建详情页的 `Artifacts` 下载 `shift-calendar-debug-apk`。

每次推送到 `main` 或 `master` 分支时，也会自动编译一次 debug APK。

当前工作流会在 GitHub 云端安装 JDK、Android SDK 和 Gradle，因此不要求仓库里必须有 `gradlew`。

## 主要文件

- `app/src/main/java/com/example/shiftcalendar/MainActivity.java`
  - 主界面、月历绘制、轮班周期、班次闹钟、节假日数据。
- `app/src/main/AndroidManifest.xml`
  - 声明 `com.android.alarm.permission.SET_ALARM` 权限。

## 更新 2027 调休

2027 年官方调休发布后，修改 `MainActivity.java` 中的 `HolidayStore`：

- `range("开始日期", "结束日期", "节日名")` 添加放假区间。
- `work("日期")` 添加调休上班日。

当前 2026 年数据按国务院办公厅正式通知预置；2027 年因当前日期为 2026-05-31，官方全年调休安排通常尚未发布，所以代码内标注为“预置”。

## 系统闹钟说明

本项目使用：

```java
new Intent(AlarmClock.ACTION_SET_ALARM)
```

这会调用手机系统闹钟应用创建真实系统闹钟。不同厂商 ROM 对批量创建闹钟、是否弹出确认界面的行为可能不同，这是 Android 系统能力本身的限制。
