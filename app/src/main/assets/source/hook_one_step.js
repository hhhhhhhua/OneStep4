import Java from "frida-java-bridge";

Java.perform(function () {

    const TAG = "OneStep40_Hook";
    const Log = Java.use("android.util.Log");
    const Log_i = Log.i.overload(
        "java.lang.String",
        "java.lang.String"
    );

    function log(...args) {
        console.log(`[${TAG}]`, ...args);

        Log_i.call(
            Log,
            TAG,
            args.map(String).join(" ")
        );
    }
    
    var DEVICE_FILTERS = {
        // 主要触摸屏控制器（不应被绑定）
        MAIN_TOUCHSCREEN: [
            "ILITEK",           // ILITEK触摸控制器
            "ilitek",
            "Synaptics",        // Synaptics触摸板
            "synaptics",
            "Goodix",           // 汇顶触摸控制器
            "goodix",
            "FTS",              // FocalTech触摸控制器
            "fts",
            "atmel",            // Atmel触摸控制器
            "Atmel",
            "cypress",          // Cypress触摸控制器 
            "Cypress",
            "TouchScreen",      // 通用触摸屏
            "touchscreen",
            "Touch",            // 包含Touch的设备
            "touch"
        ],
        
        // 虚拟/系统输入设备（通常不需要绑定）
        VIRTUAL_DEVICES: [
            "uinput",           // 用户空间输入设备
            "Virtual",          // 虚拟设备
            "virtual",
            "gpio",             // GPIO按键
            "GPIO",
            "pwrkey",           // 电源键
            "Power",            // 电源相关
            "power"
        ],
        
        // 真正的外接输入设备（应该被绑定）
        EXTERNAL_DEVICES: [
            "USB",              // USB设备
            "usb",
            "Bluetooth",        // 蓝牙设备
            "bluetooth",
            "Mouse",            // 鼠标
            "mouse",
            "Keyboard",         // 键盘
            "keyboard",
            "Gamepad",          // 游戏手柄
            "gamepad",
            "Controller",       // 控制器
            "controller"
        ]
    };
    function getHdmiDisplayId(){
      const ActivityThread = Java.use("android.app.ActivityThread");
      const DisplayManager = Java.use("android.hardware.display.DisplayManager");
      const DisplayClass = Java.use("android.view.Display");
      const context = ActivityThread.currentApplication();

      const dm = Java.cast(
          context.getSystemService("display"),
          DisplayManager
      );

      const getDisplays = DisplayManager.getDisplays.overload();

      const displays = getDisplays.call(dm);

      log("========== DISPLAY LIST ==========");

      for (let i = 0; i < displays.length; i++) {
          const d = displays[i];

          log(
              "[" + i + "]" +
              " displayId=" + d.getDisplayId() +
              " name=" + d.getName() +
              " state=" + d.getState() +
              " flags=0x" + d.getFlags().toString(16) +
              " size=" + d.getWidth() + "x" + d.getHeight()
          );
          // log(d)
          var type = DisplayClass.getType ? DisplayClass.getType.call(d) : -1;
          if (type == 2){
            return d.getDisplayId()
          }
      }

      return null;
    }

    const Activity = Java.use("android.app.Activity");
    const ActivityOptions = Java.use("android.app.ActivityOptions");
    const startActivity = Activity.startActivity.overload(
        "android.content.Intent"
    );
    startActivity.implementation = function (intent) {

        try {
            const component = intent.getComponent();

            if (component !== null) {
                const cls = component.flattenToShortString();

                log("[OneStep] startActivity: " + cls);

                if (cls === "com.sangluo.onestepc16/com.sangluo.onestep.MainActivity") {

                    let displayid = getHdmiDisplayId();
                    log(
                        "[OneStep] redirect MainActivity -> display " +
                        displayid
                    );

                    const options = ActivityOptions.makeBasic();

                    options.setLaunchDisplayId(displayid);

                    this.startActivity.overload(
                        "android.content.Intent",
                        "android.os.Bundle"
                    ).call(
                        this,
                        intent,
                        options.toBundle()
                    );

                    return;
                }
            }
        } catch (e) {
            log("[OneStep] hook error: " + e);
        }

        startActivity.call(this, intent);

    };

    const PersistentRootShell =
        Java.use("com.sangluo.onestep.system.root.PersistentRootShell");
    let persistentRootShell = PersistentRootShell.$new();

    const Display = Java.use("android.view.Display");
    const getRotation = Display.getRotation.overload();
    getRotation.implementation = function () {
        const id = this.getDisplayId();
        const ret = getRotation.call(this);
        if (id != -1 && id != 0){
          let shellCmd = "cmd window set-ignore-orientation-request -d "+ id +" true";
          let res = persistentRootShell.run(shellCmd, 10)
          // log("shellCmd: " + shellCmd + " res: " + res)
        }
        return ret;
    };

    function getIInputManager1() {
        // log("[FIX] 尝试获取IInputManager...");
        
        // 通过ServiceManager获取
        try {
            // log("[FIX] 方法1: ServiceManager.getService");
            var ServiceManager = Java.use("android.os.ServiceManager");
            var inputService = ServiceManager.getService("input");
            
            if (inputService) {
                var IInputManager = Java.use("android.hardware.input.IInputManager$Stub");
                var iInputManager = IInputManager.asInterface(inputService);
                
                // 测试方法是否可用
                try {
                    var deviceIds = iInputManager.getInputDeviceIds();
                    log("[FIX] ✅ 方法1成功，设备数量: " + deviceIds.length);
                    return iInputManager;
                } catch (e) {
                    log("[FIX] 方法1测试失败: " + e);
                }
            }
        } catch (e) {
            log("[FIX] 方法1异常: " + e);
        }
        
        log("[FIX] ❌ 所有方法都失败，无法获取可用的IInputManager");
        return null;
    }

    function getIInputManager() {
      const SysServiceObjUtil =
          Java.use("com.sangluo.onestep.utils.SysServiceObjUtil");

      const getInputManager =
          SysServiceObjUtil.getInputManager.overload();

      const inputManager = getInputManager.call(SysServiceObjUtil);
      return inputManager;
    }

     function safeCallInputManagerMethod(iInputManager, methodName, args) {
        if (!iInputManager) {
            log("[FIX] IInputManager为null，无法调用方法: " + methodName);
            return null;
        }
        
        try {
            // log("[FIX] 尝试调用方法: " + methodName);
            
            // 方法1: 直接调用
            try {
                var result;
                if (args && args.length > 0) {
                    result = iInputManager[methodName].apply(iInputManager, args);
                } else {
                    result = iInputManager[methodName]();
                }
                // log("[FIX] ✅ 直接调用成功: " + methodName);
                return result;
            } catch (e) {
                log("[FIX] 直接调用失败: " + e);
            }
            
        } catch (e) {
            log("[FIX] 调用方法异常: " + methodName + " - " + e);
        }
        
        return null;
    }

    function getInputDevicesFixed(iInputManager) {
        log("[FIX] 获取输入设备列表...");
            log("iInputManager = " + iInputManager);

        var deviceIds = safeCallInputManagerMethod(iInputManager, "getInputDeviceIds", []);
        if (!deviceIds) {
            log("[FIX] ❌ 无法获取设备ID列表");
            return [];
        }
        
        var devices = [];
        log("[FIX] 发现 " + deviceIds.length + " 个输入设备");
        
        for (var i = 0; i < deviceIds.length; i++) {
            try {
                var deviceId = deviceIds[i];
                var device = safeCallInputManagerMethod(iInputManager, "getInputDevice", [deviceId]);
                
                if (device) {
                    var deviceInfo = {
                        id: deviceId,
                        name: "未知设备",
                        descriptor: "未知",
                        external: false,
                        sources: 0
                    };
                    
                    // 安全获取设备信息
                    try {
                        deviceInfo.name = device.getName() || "未知设备";
                    } catch (e) {
                        log("[FIX] 获取设备名称失败: " + e);
                    }
                    
                    try {
                        deviceInfo.descriptor = device.getDescriptor() || "未知";
                    } catch (e) {
                        log("[FIX] 获取设备描述符失败: " + e);
                    }
                    
                    try {
                        deviceInfo.external = device.isExternal() || false;
                    } catch (e) {
                        log("[FIX] 获取外接状态失败: " + e);
                    }
                    
                    try {
                        deviceInfo.sources = device.getSources() || 0;
                    } catch (e) {
                        log("[FIX] 获取设备源失败: " + e);
                    }
                    
                    devices.push(deviceInfo);
                    
                    if (deviceInfo.external) {
                        log("[FIX] 外接设备: " + deviceInfo.name + " (ID: " + deviceInfo.id + ")");
                        // log(
                        //     " name=" + device.getName() +
                        //     " descriptor=" + device.getDescriptor() +
                        //     " sources=0x" + device.getSources().toString(16) +
                        //     " keyboardType=" + device.getKeyboardType()
                        // );
                    }else{
                        log("[FIX] 其它设备: " + deviceInfo.name + " (ID: " + deviceInfo.id + ")");
                    }
                }
            } catch (e) {
                log("[FIX] 处理设备 " + deviceIds[i] + " 失败: " + e);
            }
        }
        
        return devices;
    }
    function classifyDevice(deviceName, descriptor) {
        var name = (deviceName || "").toLowerCase();
        var desc = (descriptor || "").toLowerCase();
        
        // 检查是否为主触摸屏
        for (var i = 0; i < DEVICE_FILTERS.MAIN_TOUCHSCREEN.length; i++) {
            var keyword = DEVICE_FILTERS.MAIN_TOUCHSCREEN[i].toLowerCase();
            if (name.indexOf(keyword) !== -1 || desc.indexOf(keyword) !== -1) {
                return "MAIN_TOUCHSCREEN";
            }
        }
        
        // 检查是否为虚拟设备
        for (var i = 0; i < DEVICE_FILTERS.VIRTUAL_DEVICES.length; i++) {
            var keyword = DEVICE_FILTERS.VIRTUAL_DEVICES[i].toLowerCase();
            if (name.indexOf(keyword) !== -1 || desc.indexOf(keyword) !== -1) {
                return "VIRTUAL_DEVICE";
            }
        }
        
        // 检查是否为真正的外接设备
        for (var i = 0; i < DEVICE_FILTERS.EXTERNAL_DEVICES.length; i++) {
            var keyword = DEVICE_FILTERS.EXTERNAL_DEVICES[i].toLowerCase();
            if (name.indexOf(keyword) !== -1 || desc.indexOf(keyword) !== -1) {
                return "EXTERNAL_DEVICE";
            }
        }
        
        return "UNKNOWN";
    }
    function getContext() {
        try {
            const ActivityThread = Java.use("android.app.ActivityThread");
            var currentApplication = ActivityThread.currentApplication();
            if (currentApplication) {
                return currentApplication;
            }
        } catch (e) {
            log("[!] 获取Context失败: " + e);
        }
        return null;
    }
    function getHdmiDisplayObj(hdmiDisplayId){
        var Context = Java.use("android.content.Context");
        var displayManager = getContext().getSystemService(Context.DISPLAY_SERVICE.value);
        var DisplayManagerClass = Java.use("android.hardware.display.DisplayManager");
        var displays = DisplayManagerClass.getDisplays.call(displayManager);
        for (var i = 0; i < displays.length; i++) {
            var display = displays[i];
            var DisplayClass = Java.use("android.view.Display");
            var displayId = DisplayClass.getDisplayId.call(display);
            if(displayId ==  hdmiDisplayId){
                return display;
            }
        }
    }
    function reflectCallInputManagerMethod(obj, name, args) {
        try {
            var methods = obj.getClass().getMethods();

            for (var i = 0; i < methods.length; i++) {
                var method = methods[i];
                // log("method: " + method);

                if (method.getName() !== name) {
                    continue;
                }
                var types = method.getParameterTypes();
                if (types.length !== args.length) {
                    continue;
                }
                // log("invoke " + method);

                var javaArgs = Java.array(
                    "java.lang.Object",
                    args
                );
                method.invoke(obj, javaArgs);
                return 1;
            }

            log("not found: " + name);
        } catch (e) {
            log("safeCall error: " + e + name);
        }

        return 0;
    }
    let iInputManager_global = getIInputManager();
    function bindDeviceToDisplayFixed(device, displayId) {
        // log(device.name, device.descriptor)
        const classification = classifyDevice(device.name, device.descriptor);
        if(classification != "EXTERNAL_DEVICE"){
            log("[FIX] 跳过非外接设备: " + device.name);
            return false;
        }

        log("[FIX] 绑定设备到显示器: " + device.name + " -> " + displayId);
        
        if (!device.external) {
            log("[FIX] 跳过非外接设备: " + device.name);
            return false;
        }
        
        try {
            
            try {
                var hdmiDisplayObj = getHdmiDisplayObj(displayId);
                var uniqueId = hdmiDisplayObj.getUniqueId();
                var result = reflectCallInputManagerMethod(iInputManager_global, "removeUniqueIdAssociationByDescriptor", [device.descriptor]);
                result = reflectCallInputManagerMethod(iInputManager_global, "addUniqueIdAssociationByDescriptor", [device.descriptor, String(uniqueId)]);
                if (result !== 0) {
                    log("[FIX] ✅ addPortAssociation绑定成功");
                    return true;
                }
            } catch (e) {
                log("[FIX] addPortAssociation失败: " + e);
            }
            

            return false;
        } catch (e) {
            log("[FIX] 绑定设备异常: " + e);
            return false;
        }
    }
    function startFixedPollingMonitor(displayIdNew) {
      let displayId = null;
      if (displayIdNew != null){
        displayId = displayIdNew; 
      }else{
        displayId = getHdmiDisplayId(); 
      }
      if(displayId == null){
        log("❌ 请确认外接屏幕已经连接~");
        return;
      }
      
      var iInputManager = getIInputManager1();
      if (!iInputManager) {
          log("[FIX] ❌ 无法获取IInputManager，监控无法启动");
          return false;
      }
      var lastDevices = [];
      var boundDevices = [];
      
      // 绑定现有外接设备
      try {
          var initialDevices = getInputDevicesFixed(iInputManager);
          log("[FIX] 初始化发现 " + initialDevices.length + " 个设备");
          
          var externalDevices = initialDevices.filter(function(d) { return d.external; });
          log("[FIX] 其中 " + externalDevices.length + " 个外接设备");
          
          for (var i = 0; i < externalDevices.length; i++) {
              if (bindDeviceToDisplayFixed(externalDevices[i], displayId)) {
                  boundDevices.push(externalDevices[i]);
              
              }
          }
          
          log("[FIX] 初始绑定完成，已绑定 " + boundDevices.length + " 个设备");
          lastDevices = initialDevices;

          
      } catch (e) {
          log("[FIX] 初始化绑定异常: " + e);
      }
      
      return true;
    }

    function  startFixedPollingMonitorSync(displayId){
        setTimeout(function () {
            Java.perform(function () {
                try {
                    log("[FIX] delayed monitor start, displayId=" + displayId);
                    startFixedPollingMonitor(displayId);
                } catch (e) {
                    log("[FIX] monitor error: " + e);
                }
            });
        }, 0);
    }

    let FridaBridge = Java.use("com.sangluo.onestep.frida.FridaBridge");
    FridaBridge["startFixedPollingMonitorSyncBridge"].implementation = function (displayId) {
        log(`FridaBridge.startFixedPollingMonitorSyncBridge is called: displayId=${displayId}`);
        startFixedPollingMonitorSync(displayId)
        this["startFixedPollingMonitorSyncBridge"](displayId);
        
    };
    FridaBridge["getHdmiDisplayIdBridge"].implementation = function () {
        let displayid = getHdmiDisplayId();
        const Integer = Java.use("java.lang.Integer");
        let javaDisplayId = Integer.valueOf(displayid);
        log("displayid: ", javaDisplayId);
        return javaDisplayId;
    };
   
});