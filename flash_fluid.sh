#!/bin/bash
# $1: DEVICE ID, $2: TARGET MODEL

function build_notify()
{
	(zenity --info --display=:0.0 --timeout=10 --title="Notification for AOSP flash" --text="<b><span color=\"green\">#### flash completed successfully ####</span></b>") &
	(sleep 2 && wmctrl -F -r "Notification for AOSP flash" -b add,above)
}

if [ $# -eq 3 ] && [ $3 = "-f" ]
then
	adb -s $1 wait-for-device
	echo "======== [$1] Flash MobileDUI ROM ========"
	echo "======= [$1] Start bootloader mode ======="
	adb -s $1 reboot bootloader

	if [ $2 = "marlin" ]
	then
		echo "========= [$1] Flash custom rom =========="
		fastboot -s $1 flash boot_a out/target/product/$2/boot.img
		fastboot -s $1 flash system_a out/target/product/$2/system.img
		fastboot -s $1 flash vendor_a out/target/product/$2/vendor.img
		#fastboot -s $1 flash userdata out/target/product/$2/userdata.img
		fastboot -s $1 set_active a
	elif [ $2 = "dragon" ]
	then
		fastboot -s $1 flash boot out/target/product/$2/boot.img
		fastboot -s $1 flash system out/target/product/$2/system.img
		fastboot -s $1 flash vendor out/target/product/$2/vendor.img
		#fastboot format userdata
		#fastboot format cache
	fi
	echo "============== [$1] Reboot ==============="
	fastboot -s $1 reboot
	adb -s $1 wait-for-device
	echo "======== [$1] Get root permission ========"
	adb -s $1 root
	echo "root permission."
	sleep 2
	adb -s $1 wait-for-device
	adb -s $1 shell chmod 777 /data/data/com.fluid.wrapperapp
	adb -s $1 wait-for-device
	echo "===== [$1] Disable verity and reboot ====="
	adb -s $1 disable-verity
	adb -s $1 wait-for-device
	echo "============== [$1] Reboot ==============="
	adb -s $1 reboot
	adb -s $1 wait-for-device
	adb -s $1 wait-for-device
	echo "======== [$1] Get root permission ========"
	adb -s $1 root
	echo "root permission."
	sleep 2
	echo "========== [$1] Remount /system =========="
	adb -s $1 remount
	adb -s $1 wait-for-device
	adb -s $1 wait-for-device
	echo "===== [$1] Push some .so libraries ======="
	adb -s $1 push ./mobiledui_init/PrebuiltGmsCore_lib/arm64-v8a/* /system/lib64/
	echo "===== [$1] Push MobileDUI excutable & libraries ======="
	adb -s $1 push ./out/target/product/$2/system/bin/fluidmanager /system/bin/
	adb -s $1 push ./out/target/product/$2/system/lib64/libfluid.so /system/lib64/
	echo "===== [$1] Configure some properties ======="
	adb -s $1 shell stop
	adb -s $1 shell setprop dalvik.vm.usejit false
	adb -s $1 shell start
	adb -s $1 shell setprop pm.dexopt.install everything
	adb -s $1 shell getprop pm.dexopt.install 
	adb -s $1 shell setenforce 0
	adb -s $1 shell stop mpdecision

	adb -s $1 shell "echo 1 > /sys/devices/system/cpu/cpu1/online"
	adb -s $1 shell "echo 1 > /sys/devices/system/cpu/cpu2/online"
	adb -s $1 shell "echo 1 > /sys/devices/system/cpu/cpu3/online"
	adb -s $1 shell "echo "performance" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
	adb -s $1 shell "echo "performance" > /sys/devices/system/cpu/cpu1/cpufreq/scaling_governor"
	adb -s $1 shell "echo "performance" > /sys/devices/system/cpu/cpu2/cpufreq/scaling_governor"
	adb -s $1 shell "echo "performance" > /sys/devices/system/cpu/cpu3/cpufreq/scaling_governor"

	(build_notify)
elif [ $# -eq 2 ]
then
	adb -s $1 wait-for-device
	echo "======== [$1] Get root permission ========"
	adb -s $1 root
	echo "root permission."
	adb -s $1 wait-for-device
	adb -s $1 shell chmod 777 /data/data/com.fluid.wrapperapp
	adb -s $1 wait-for-device
	echo "========== [$1] Remount /system =========="
	adb -s $1 remount
	adb -s $1 wait-for-device
	adb -s $1 wait-for-device
	echo "===== [$1] Push some .so libraries ======="
	adb -s $1 push ./mobiledui_init/PrebuiltGmsCore_lib/arm64-v8a/* /system/lib64/
	echo "===== [$1] Push MobileDUI excutable & libraries ======="
	adb -s $1 push ./out/target/product/$2/system/bin/fluidmanager /system/bin/
	adb -s $1 push ./out/target/product/$2/system/lib64/libfluid.so /system/lib64/
	echo "===== [$1] Configure some properties ======="
	adb -s $1 shell stop
	adb -s $1 shell setprop dalvik.vm.usejit false
	adb -s $1 shell start
	adb -s $1 shell setprop pm.dexopt.install everything
	adb -s $1 shell getprop pm.dexopt.install 
	adb -s $1 shell setenforce 0
	adb -s $1 shell stop mpdecision

	adb -s $1 shell "echo 1 > /sys/devices/system/cpu/cpu1/online"
	adb -s $1 shell "echo 1 > /sys/devices/system/cpu/cpu2/online"
	adb -s $1 shell "echo 1 > /sys/devices/system/cpu/cpu3/online"
	adb -s $1 shell "echo "performance" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
	adb -s $1 shell "echo "performance" > /sys/devices/system/cpu/cpu1/cpufreq/scaling_governor"
	adb -s $1 shell "echo "performance" > /sys/devices/system/cpu/cpu2/cpufreq/scaling_governor"
	adb -s $1 shell "echo "performance" > /sys/devices/system/cpu/cpu3/cpufreq/scaling_governor"

	(build_notify)
else
	echo "USAGE: ./flash_mobiledui.sh [DEVICE_ID] [TARGET_PRODUCT] [-f (optional)]"
fi
