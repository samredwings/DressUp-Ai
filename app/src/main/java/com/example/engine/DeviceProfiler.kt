package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.io.RandomAccessFile
import java.util.regex.Pattern

/**
 * Detects device capabilities to automatically select the best processing mode.
 * Works on all Android versions from API 26+.
 */
class DeviceProfiler(private val context: Context) {

    data class DeviceCapability(
        val totalRamMB: Long,
        val availableRamMB: Long,
        val cpuCores: Int,
        val cpuMaxFreqMHz: Int,
        val supportsOpenCL: Boolean,
        val supportsVulkan: Boolean,
        val supportsNNAPI: Boolean,
        val storageFreeMB: Long,
        val supportsMode1: Boolean = true,  // Ultra-light: always works
        val supportsMode2: Boolean,            // On-device AI
        val supportsMode3: Boolean = true,   // Free cloud: always works (needs internet)
        val recommendedMode: TryOnPipelineV3.ProcessingMode
    )

    /**
     * Analyzes the device and returns capability info.
     * Safe to call from any thread.
     */
    fun analyze(): DeviceCapability {
        val totalRam = getTotalRamMB()
        val availableRam = getAvailableRamMB()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val cpuFreq = getMaxCpuFreqMHz()
        val storageFree = getFreeStorageMB()

        // Determine mode support based on specs
        val supportsMode2 = when {
            totalRam >= 6144 -> true   // 6GB+ RAM: full models
            totalRam >= 4096 && cpuCores >= 6 -> true  // 4GB + 6 cores: lite models
            totalRam >= 3072 && cpuCores >= 8 -> true  // 3GB + 8 cores: minimal models
            else -> false
        }

        val recommendedMode = when {
            totalRam >= 4096 && supportsMode2 -> TryOnPipelineV3.ProcessingMode.ON_DEVICE_AI
            else -> TryOnPipelineV3.ProcessingMode.ULTRA_LIGHT
        }

        return DeviceCapability(
            totalRamMB = totalRam,
            availableRamMB = availableRam,
            cpuCores = cpuCores,
            cpuMaxFreqMHz = cpuFreq,
            supportsOpenCL = checkOpenCLSupport(),
            supportsVulkan = checkVulkanSupport(),
            supportsNNAPI = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
            storageFreeMB = storageFree,
            supportsMode2 = supportsMode2,
            recommendedMode = recommendedMode
        )
    }

    /**
     * Returns a human-readable summary of device capabilities.
     */
    fun getDeviceSummary(): String {
        val cap = analyze()
        return buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("RAM: ${cap.totalRamMB}MB total / ${cap.availableRamMB}MB available")
            appendLine("CPU: ${cap.cpuCores} cores @ ~${cap.cpuMaxFreqMHz}MHz")
            appendLine("Storage Free: ${cap.storageFreeMB}MB")
            appendLine("GPU: OpenCL=${cap.supportsOpenCL}, Vulkan=${cap.supportsVulkan}, NNAPI=${cap.supportsNNAPI}")
            appendLine()
            appendLine("Supported Modes:")
            appendLine("  Mode 1 (Ultra-Light): YES (always)")
            appendLine("  Mode 2 (On-Device AI): ${if (cap.supportsMode2) "YES" else "NO"}")
            appendLine("  Mode 3 (Free Cloud): YES (with internet)")
            appendLine()
            appendLine("Recommended: ${cap.recommendedMode.name}")
        }
    }

    // ---------------------------------------------------------------- private helpers

    private fun getTotalRamMB(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem / (1024 * 1024)
        } catch (_: Exception) {
            // Fallback: read from /proc/meminfo
            try {
                val reader = RandomAccessFile("/proc/meminfo", "r")
                val line = reader.readLine()
                reader.close()
                val matcher = Pattern.compile("(\d+)").matcher(line)
                if (matcher.find()) {
                    matcher.group(1).toLong() / 1024  // Convert KB to MB
                } else {
                    2048  // Default assumption: 2GB
                }
            } catch (_: Exception) {
                2048
            }
        }
    }

    private fun getAvailableRamMB(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.availMem / (1024 * 1024)
        } catch (_: Exception) {
            512  // Conservative default
        }
    }

    private fun getMaxCpuFreqMHz(): Int {
        var maxFreq = 0
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            try {
                val reader = RandomAccessFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq", "r")
                val freq = reader.readLine().toInt() / 1000  // Convert KHz to MHz
                reader.close()
                if (freq > maxFreq) maxFreq = freq
            } catch (_: Exception) {
                // Skip this core
            }
        }
        return if (maxFreq > 0) maxFreq else 1500  // Default: 1.5GHz
    }

    private fun getFreeStorageMB(): Long {
        return try {
            val stat = android.os.StatFs(context.filesDir.path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            (availableBlocks * blockSize) / (1024 * 1024)
        } catch (_: Exception) {
            1024  // Default: 1GB free
        }
    }

    private fun checkOpenCLSupport(): Boolean {
        return try {
            val libraries = listOf(
                "/system/vendor/lib/libOpenCL.so",
                "/system/vendor/lib64/libOpenCL.so",
                "/system/lib/libOpenCL.so",
                "/system/lib64/libOpenCL.so",
                "/system/vendor/lib/egl/libGLES_mali.so",
                "/system/vendor/lib64/egl/libGLES_mali.so",
                "/system/lib/egl/libGLES_mali.so",
                "/system/lib64/egl/libGLES_mali.so"
            )
            libraries.any { File(it).exists() }
        } catch (_: Exception) {
            false
        }
    }

    private fun checkVulkanSupport(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (
            context.packageManager.hasSystemFeature("android.hardware.vulkan.level") ||
            context.packageManager.hasSystemFeature("android.hardware.vulkan.version")
        )
    }
}
