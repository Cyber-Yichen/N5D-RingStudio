## 源码构建

作者：**Cyber-Yichen**。

源码使用原生 Java 与 Android 系统组件，无第三方 UI 框架。以下构建流程在 WSL / Linux、OpenJDK 11 环境测试。

依赖：OpenJDK 11、Python 3、Android SDK android-23/android.jar、aapt、zipalign、apksigner，以及支持 D8 的 R8 JAR。将工具加入 PATH。Android SDK 可从 Android 官方开发工具获取，R8 可从 Google 的 R8 发布存储获取；仓库不分发这些工具或系统组件。

```bash
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export ANDROID_JAR=/path/to/android-23/android.jar
export R8_JAR=/path/to/r8.jar
bash build.sh
```

未配置密钥时生成未签名 APK。签名构建另需设置 KEYSTORE、KS_ALIAS、KS_PASSWORD 和 KEY_PASSWORD 环境变量。密钥与密码不应提交到仓库。发行版签名私钥不包含在此项目中。


Android 图标位于 res，资源打包需要 aapt 的 -S res 参数。
