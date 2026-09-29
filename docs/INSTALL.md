# Installing Taski · نصب تسکی

## English

1. Copy `taski-<version>-release.apk` to your phone (Android 8.0 or newer).
2. Open it and allow installing from this source when Android asks.
3. On first launch, allow notifications so reminders and the focus timer can reach you.
   For reminders at the exact minute, allow **Alarms & reminders** when the app's
   Reminders settings suggest it.

This build is signed with Taski's **public example key** (`signing/example-release.jks`).
That is fine for your own phone. To publish, or to keep updates tied to a key only you
hold, build with your own key: put a `keystore.properties` in the project root
(see `signing/keystore.properties.example`) and run `./gradlew :app:zipRelease`.
An app signed with one key cannot be updated in place by a build signed with another.

## فارسی

۱. فایل `taski-<version>-release.apk` را به گوشی منتقل کنید (اندروید ۸ یا جدیدتر).
۲. آن را باز کنید و وقتی اندروید پرسید، اجازه نصب از این منبع را بدهید.
۳. در اولین اجرا اجازه اعلان‌ها را بدهید تا یادآوری‌ها و تایمر تمرکز به شما برسند. برای
   یادآوری دقیق سر دقیقه، وقتی تنظیمات یادآوری پیشنهاد داد، اجازه **هشدارها و یادآوری‌ها** را هم بدهید.

این نسخه با **کلید نمونه عمومی** تسکی (`signing/example-release.jks`) امضا شده است. برای
استفاده روی گوشی خودتان کافی است. برای انتشار، یا اگر می‌خواهید به‌روزرسانی‌ها فقط با کلید
خودتان ممکن باشد، فایل `keystore.properties` را در ریشه پروژه بگذارید (نمونه:
`signing/keystore.properties.example`) و `./gradlew :app:zipRelease` را اجرا کنید.
برنامه‌ای که با یک کلید امضا شده، با نسخه‌ای که کلید دیگری دارد به‌روزرسانی نمی‌شود.
