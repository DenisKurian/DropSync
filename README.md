# ⚡ DropSync
**A Secure, High-Speed Peer-to-Peer File Sharing App for Android**

[![Download APK](https://img.shields.io/badge/Download-Release_APK-00C853?style=for-the-badge&logo=android&logoColor=white)](https://github.com/DenisKurian/DropSync/releases/download/v1.0.0/app-release.apk)

DropSync is a robust peer-to-peer file transfer system engineered to securely send files across devices without relying on an active internet connection. It utilizes Bluetooth Low Energy (BLE) for seamless device discovery and WiFi Direct for high-speed data transmission.

---

## ✨ Key Features
* 🚀 **High-Speed Transfers:** Uses WiFi Direct for fast, reliable payload deliveries.
* 🔒 **End-to-End Encryption:** Secures BLE messaging and file transfers with an AES-256 encryption layer.
* 📊 **Real-Time Analytics:** Built-in performance tracking (BLE RTT and transfer timing) designed to overcome cross-device clock drift.
* 🎨 **Modern Material UI:** Features a premium UI built with Jetpack Compose, including automatic Light/Dark mode adaptation.
* 📁 **Secure Storage:** Integrated directly with the Android 10+ MediaStore API to ensure files save correctly without corruption.

## 📱 How to Use
*Note: To test the peer-to-peer features, you must install the application on **two** separate Android devices.*

1. Click the **Download APK** button above to grab the latest release.
2. Transfer the `.apk` file to your Android devices.
3. Open the file to install (you may need to allow *Install from Unknown Sources* in your device settings).
4. Launch the app, grant the necessary Bluetooth/WiFi permissions, and start sharing!

## 🛠️ Tech Stack
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material Theme)
* **Networking:** Android WiFi Direct (P2P), Bluetooth Low Energy (BLE), Socket.IO
* **Security:** Java Cryptography Architecture (AES-256)

---
