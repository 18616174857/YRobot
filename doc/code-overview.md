## Code Overview (12/16/19)

#### Relevant Source Files
`android-app/app/src/main/java/com/yrobot/exo/`
```
├── app
│   ├── MainActivity.java
│   ├── ConnectedPeripheralFragment.java (Base Fragment for fragments under app/views/<name>Fragment.java, handles communication)
│   ├── data
│   │   ├── ChartManager.java
│   │   ├── DataPacket.java
│   │   ├── ExoData.java
│   │   ├── FirmwareManager.java
│   │   ├── GaitTuningData.java
│   │   ├── LegData.java
│   │   ├── MotorData.java
│   │   ├── Param.java
│   │   ├── ParamManager.java
│   │   └── SeekBarManager.java
│   ├── utils
│   │   ├── CRC8.java (calculate CRC)
│   │   ├── MsgStat.java (Communication statistics - count & rate)
│   │   └── State.java
│   ├── views
│   │   ├── ControlFragment.java
│   │   ├── DataFragment.java
│   │   ├── InfoFragment.java
│   │   ├── ParamFragment.java
│   │   └── UserStatusFragment.java
│   └── YrConstants.java
└── YRobotApplication.java
```

#### Main Views/Fragment XML Files
`android-app/app/src/main/res/`
```
layout/
├── activity_main.xml
├── fragment_control.xml
├── fragment_data.xml
├── fragment_info.xml
├── fragment_param.xml
└── fragment_user_status.xml
```

#### Bluetooth Communication files
`android-app/app/src/main/java/com/yrobot/exo/ble/`

```
├── BleUtils.java
├── central
│   ├── BleManager.java
│   ├── BlePeripheralBattery.java
│   ├── BlePeripheralDfu.java
│   ├── BlePeripheral.java
│   ├── BlePeripheralUart.java
│   ├── BleScanner.java
│   ├── BleUUIDNames.java
│   ├── UartDataManager.java
│   └── UartPacketManager.java
├── peripheral
│   ├── DeviceInformationPeripheralService.java
│   ├── GattServer.java
│   ├── PeripheralService.java
│   ├── UartPeripheralModePacketManager.java
│   └── UartPeripheralService.java
├── UartPacket.java
└── UartPacketManagerBase.java
```
