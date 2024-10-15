# Bracelets, Cones, and Master System

This repository contains two key components: 
1. **Arduino Firmware** for controlling intelligent bracelets, cones, and the master device.
2. **Safety Shield - Android App** for monitoring and simulating system behavior through a user-friendly interface.

## 1. Arduino Firmware

The Arduino files manage the functionality of the system's hardware components, including communication and control of bracelets, cones, and the master.

- **`bracciali_full_Op2.ino`**: Firmware for the bracelets, handling operations like communication and interaction with external signals.
- **`cono_ble.ino`**: Controls the cone using Bluetooth Low Energy (BLE).
- **`cono_rf.ino`**: Controls the cone via radio frequency (RF).
- **`ricezione_master_OK_NICCO_.ino`**: Manages data reception from the master device.

## 2. Safety Shield - Android App

The Android app provides real-time visualization and simulation of the system's state, focusing on the intelligent bracelets.

### Key Features:

- **Real-time Monitoring**: Displays bracelet status (battery and availability).
- **Simulation**: Simulates different system scenarios.
- **Easy-to-Use Interface**: Intuitive controls for system management.

### Setup:

1. Clone the repository and open the Android project in Android Studio.
2. Build and run the app on an Android device or emulator.
3. Use the app to visualize bracelet states or simulate scenarios.