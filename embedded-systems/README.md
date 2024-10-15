# Bracelets, Cones, and Master Project

This repository contains the Arduino code (`.ino` files) used to control various components of a system consisting of bracelets, cones, and a master controller. Each file plays a specific role in handling communication and operations within the system.

## Description of `.ino` Files

### 1. `bracciali_full_Op2.ino`
This file contains the complete firmware for controlling the **bracelets**. It manages the primary operations of the bracelet, such as communication with other components of the system and interactions with users or external signals.

### 2. `cono_ble.ino`
This file handles the operations of the **cone** using **Bluetooth Low Energy (BLE)**. The cone likely communicates with other devices via BLE, managing the transmission and reception of data necessary for its functionality.

### 3. `cono_rf.ino`
The `cono_rf.ino` file controls the **cone** using **radio frequency (RF)** communication. This code handles sending and receiving signals via RF to coordinate with other devices, as an alternative to BLE.

### 4. `ricezione_master_OK_NICCO_.ino`
This file is responsible for **receiving data from the master** device, likely by a central unit or another component that coordinates the overall system operation. The code focuses on correctly receiving and processing the information transmitted by the master.